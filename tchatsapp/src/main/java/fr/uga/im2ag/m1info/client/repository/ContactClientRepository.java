package fr.uga.im2ag.m1info.client.repository;

import fr.uga.im2ag.m1info.client.model.ContactClient;
import fr.uga.im2ag.m1info.client.model.ContactRequest;
import fr.uga.im2ag.m1info.common.repository.AbstractRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ContactClientRepository extends AbstractRepository<Integer, ContactClient> {

    /**
     * Default expiration time for contact requests (7 days).
     */
    public static final Duration DEFAULT_REQUEST_EXPIRATION = Duration.ofDays(7);

    private final Map<String, ContactRequest> pendingRequests; // key: requestId
    private final Map<Integer, String> sentRequests; // key: receiverId, value: requestId
    private final Map<Integer, String> receivedRequests; // key: senderId, value: requestId

    public ContactClientRepository(Map<Integer, ContactClient> contacts,
                                   Map<String, ContactRequest> pendingRequests,
                                   Map<Integer, String> sentRequests,
                                   Map<Integer, String> receivedRequests) {
        super(contacts);
        this.pendingRequests = pendingRequests;
        this.sentRequests = sentRequests;
        this.receivedRequests = receivedRequests;
    }

    public ContactClientRepository() {
        this(new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.ConcurrentHashMap<>());
    }

    @Override
    protected Integer getKey(ContactClient entity) {
        return entity.getContactId();
    }

    /* ----------------------- Contact Management ----------------------- */

    /**
     * Check if a user is already a contact.
     *
     * @param contactId the contact ID to check
     * @return true if the user is a contact
     */
    public boolean isContact(int contactId) {
        return storage.containsKey(contactId);
    }

    /* ----------------------- Contact Request Management ----------------------- */

    /**
     * Add a sent contact request (request we initiated).
     *
     * @param request the contact request
     * @throws IllegalArgumentException if a request to this user already exists
     */
    public void addSentRequest(ContactRequest request) {
        if (sentRequests.containsKey(request.getReceiverId())) {
            throw new IllegalArgumentException("A request to user " + request.getReceiverId() + " already exists");
        }
        pendingRequests.put(request.getRequestId(), request);
        sentRequests.put(request.getReceiverId(), request.getRequestId());
    }

    /**
     * Add a received contact request (request someone sent to us).
     *
     * @param request the contact request
     * @throws IllegalArgumentException if a request from this user already exists
     */
    public void addReceivedRequest(ContactRequest request) {
        if (receivedRequests.containsKey(request.getSenderId())) {
            throw new IllegalArgumentException("A request from user " + request.getSenderId() + " already exists");
        }
        pendingRequests.put(request.getRequestId(), request);
        receivedRequests.put(request.getSenderId(), request.getRequestId());
    }

    /**
     * Get a contact request by its ID.
     *
     * @param requestId the request ID
     * @return the contact request, or null if not found
     */
    public ContactRequest getRequest(String requestId) {
        return pendingRequests.get(requestId);
    }

    /**
     * Check if there is a pending sent request to a user.
     *
     * @param receiverId the receiver user ID
     * @return true if a pending request exists
     */
    public boolean hasSentRequestTo(int receiverId) {
        String requestId = sentRequests.get(receiverId);
        if (requestId == null) return false;
        ContactRequest request = pendingRequests.get(requestId);
        return request != null && request.isActive();
    }

    /**
     * Check if there is a pending received request from a user.
     *
     * @param senderId the sender user ID
     * @return true if a pending request exists
     */
    public boolean hasReceivedRequestFrom(int senderId) {
        String requestId = receivedRequests.get(senderId);
        if (requestId == null) return false;
        ContactRequest request = pendingRequests.get(requestId);
        return request != null && request.isActive();
    }

    /**
     * Get the sent request to a specific user.
     *
     * @param receiverId the receiver user ID
     * @return the contact request, or null if not found
     */
    public ContactRequest getSentRequestTo(int receiverId) {
        String requestId = sentRequests.get(receiverId);
        return requestId != null ? pendingRequests.get(requestId) : null;
    }

    /**
     * Get the received request from a specific user.
     *
     * @param senderId the sender user ID
     * @return the contact request, or null if not found
     */
    public ContactRequest getReceivedRequestFrom(int senderId) {
        String requestId = receivedRequests.get(senderId);
        return requestId != null ? pendingRequests.get(requestId) : null;
    }

    /**
     * Get all pending received requests.
     *
     * @return set of received contact requests that are still active
     */
    public Set<ContactRequest> getPendingReceivedRequests() {
        return receivedRequests.values().stream()
                .map(pendingRequests::get)
                .filter(ContactRequest::isActive)
                .collect(Collectors.toSet());
    }

    /**
     * Get all pending sent requests.
     *
     * @return set of sent contact requests that are still active
     */
    public Set<ContactRequest> getPendingSentRequests() {
        return sentRequests.values().stream()
                .map(pendingRequests::get)
                .filter(ContactRequest::isActive)
                .collect(Collectors.toSet());
    }

    /**
     * Remove a contact request.
     *
     * @param requestId the request ID to remove
     */
    public void removeRequest(String requestId) {
        ContactRequest request = pendingRequests.remove(requestId);
        if (request != null) {
            sentRequests.remove(request.getReceiverId());
            receivedRequests.remove(request.getSenderId());
        }
    }

    /**
     * Clean up expired requests.
     * Should be called periodically.
     *
     * @return number of expired requests removed
     */
    public int cleanupExpiredRequests() {
        int removed = 0;
        var expired = pendingRequests.values().stream()
                .filter(ContactRequest::isExpired)
                .map(ContactRequest::getRequestId)
                .toList();

        for (String requestId : expired) {
            removeRequest(requestId);
            removed++;
        }
        return removed;
    }

    /**
     * Create a new contact request with default expiration time.
     *
     * @param requestId unique identifier for the request
     * @param senderId sender user ID
     * @param receiverId receiver user ID
     * @return the created contact request
     */
    public static ContactRequest createRequest(String requestId, int senderId, int receiverId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(DEFAULT_REQUEST_EXPIRATION);
        return new ContactRequest(requestId, senderId, receiverId, now, expiresAt);
    }
}