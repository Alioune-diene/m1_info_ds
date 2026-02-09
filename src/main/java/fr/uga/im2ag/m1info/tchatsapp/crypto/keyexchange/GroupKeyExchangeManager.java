package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

import fr.uga.im2ag.m1info.tchatsapp.common.model.GroupInfo;
import fr.uga.im2ag.m1info.tchatsapp.common.repository.GroupRepository;
import fr.uga.im2ag.m1info.tchatsapp.crypto.KeyExchange;
import fr.uga.im2ag.m1info.tchatsapp.crypto.SessionKeyManager;
import fr.uga.im2ag.m1info.tchatsapp.crypto.SymmetricCipher;
import fr.uga.im2ag.m1info.tchatsapp.storage.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced GroupKeyExchangeManager with automatic session establishment.
 * <p>
 * This version automatically establishes private ECDH sessions with members
 * who don't have one before distributing group keys.
 */
public class GroupKeyExchangeManager implements IKeyExchangeManager {

    private static final Logger LOG = Logger.getLogger(GroupKeyExchangeManager.class.getName());

    // ========================= Constants =========================

    private static final int AES_KEY_SIZE = 256;
    private static final String GROUP_PREFIX = "group_";
    private static final String PRIVATE_PREFIX = "private_";
    private static final long SESSION_ESTABLISH_TIMEOUT_MS = 10000;

    // ========================= Dependencies =========================

    private final int localClientId;
    private final SessionKeyManager sessionManager;
    private final GroupRepository groupRepository;
    private final KeyStore keyStore;
    private final KeyExchange keyExchange;
    private final SymmetricCipher cipher;

    // Reference to private manager for establishing sessions
    private PrivateKeyExchangeManager privateManager;

    // ========================= State =========================

    private final Map<Integer, PendingGroupKeyDistribution> pendingDistributions;
    private final List<KeyExchangeListener> listeners;
    private volatile Consumer<KeyExchangeMessageData> messageSender;
    private volatile boolean running;
    private final SecureRandom secureRandom;
    private final Map<Integer, Set<Integer>> groupMembersCache;

    // Pending session establishments
    private final Map<Integer, CompletableFuture<Boolean>> pendingSessions;

    // ========================= Constructor =========================

    public GroupKeyExchangeManager(
            int localClientId,
            SessionKeyManager sessionManager,
            GroupRepository groupRepository,
            KeyStore keyStore) {

        if (localClientId <= 0) {
            throw new IllegalArgumentException("Local client ID must be positive: " + localClientId);
        }
        Objects.requireNonNull(sessionManager, "Session manager cannot be null");
        Objects.requireNonNull(groupRepository, "Group repository cannot be null");

        this.localClientId = localClientId;
        this.sessionManager = sessionManager;
        this.groupRepository = groupRepository;
        this.keyStore = keyStore;
        this.keyExchange = new KeyExchange();
        this.cipher = new SymmetricCipher();

        this.pendingDistributions = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.secureRandom = new SecureRandom();
        this.groupMembersCache = new ConcurrentHashMap<>();
        this.pendingSessions = new ConcurrentHashMap<>();
        this.running = false;
    }

    /**
     * Sets the private key exchange manager for establishing sessions.
     * Should be called by CompositeKeyExchangeManager.
     */
    public void setPrivateManager(PrivateKeyExchangeManager privateManager) {
        this.privateManager = privateManager;
    }

    // ========================= IKeyExchangeManager Implementation =========================

    @Override
    public boolean canHandle(int conversationId) {
        GroupInfo group = groupRepository.findById(conversationId);
        return group != null;
    }

    @Override
    public void initiateKeyExchange(int targetId) throws KeyExchangeException {
        if (!running) {
            throw new KeyExchangeException("Manager not running", KeyExchangeException.ErrorCode.INTERNAL_ERROR);
        }

        GroupInfo group = groupRepository.findById(targetId);
        if (group == null) {
            throw new KeyExchangeException("Group not found: " + targetId, KeyExchangeException.ErrorCode.INVALID_PEER_ID, targetId);
        }

        if (group.getAdminId() != localClientId) {
            throw new KeyExchangeException("Only group admin can initiate key distribution", KeyExchangeException.ErrorCode.PERMISSION_DENIED, targetId);
        }

        initiateGroupKeyDistribution(targetId, group);
    }

    @Override
    public void handleKeyExchangeRequest(int peerId, byte[] data) throws KeyExchangeException {
        if (!GroupKeyExchangeData.isGroupKeyExchange(data)) {
            LOG.fine("Not a group key exchange, ignoring");
            return;
        }

        GroupKeyExchangeData exchangeData = GroupKeyExchangeData.decode(data);
        int groupId = exchangeData.getGroupId();

        LOG.info(String.format("Received group key from peer %d for group %d", peerId, groupId));

        GroupInfo group = groupRepository.findById(groupId);
        if (group == null) {
            throw new KeyExchangeException("Unknown group: " + groupId, KeyExchangeException.ErrorCode.INVALID_PEER_ID, groupId);
        }

        if (group.getAdminId() != peerId) {
            throw new KeyExchangeException("Key sender is not group admin", KeyExchangeException.ErrorCode.PERMISSION_DENIED, peerId);
        }

        String adminConversation = PRIVATE_PREFIX + peerId;
        SecretKey adminSession = sessionManager.getSessionKey(adminConversation);

        if (adminSession == null) {
            throw new KeyExchangeException("No session with group admin", KeyExchangeException.ErrorCode.NO_SESSION, peerId);
        }

        try {
            byte[] groupKeyBytes = decryptGroupKey(exchangeData.getEncryptedGroupKey(), adminSession);
            SecretKey groupKey = new SecretKeySpec(groupKeyBytes, "AES");

            String groupConversation = GROUP_PREFIX + groupId;
            sessionManager.storeSessionKey(groupConversation, groupKey);

            updateGroupMembersCache(groupId, group);

            LOG.info(String.format("Successfully stored group key for group %d", groupId));

            notifyGroupKeyReceived(groupId, peerId);
            sendGroupKeyAck(peerId, groupId);

        } catch (Exception e) {
            throw new KeyExchangeException("Failed to process group key", KeyExchangeException.ErrorCode.CRYPTO_FAILURE, groupId, e);
        }
    }

    @Override
    public void handleKeyExchangeResponse(int peerId, byte[] data) throws KeyExchangeException {
        if (!GroupKeyExchangeData.isGroupKeyAck(data)) {
            return;
        }

        GroupKeyExchangeData ackData = GroupKeyExchangeData.decode(data);
        int groupId = ackData.getGroupId();

        LOG.info(String.format("Received group key ACK from peer %d for group %d", peerId, groupId));

        PendingGroupKeyDistribution pending = pendingDistributions.get(groupId);
        if (pending != null) {
            pending.markMemberComplete(peerId);

            if (pending.isComplete()) {
                pendingDistributions.remove(groupId);
                notifyGroupKeyDistributionComplete(groupId);
            }
        }
    }

    @Override
    public boolean hasSessionWith(int targetId) {
        String conversationId = GROUP_PREFIX + targetId;
        return sessionManager.hasSession(conversationId);
    }

    @Override
    public void invalidateSession(int targetId) {
        invalidateSession(targetId, "Manual invalidation");
    }

    @Override
    public void invalidateSession(int targetId, String reason) {
        String conversationId = GROUP_PREFIX + targetId;
        sessionManager.removeSession(conversationId);
        groupMembersCache.remove(targetId);

        LOG.info(String.format("Invalidated group %d session: %s", targetId, reason));

        for (KeyExchangeListener listener : listeners) {
            try {
                listener.onSessionInvalidated(targetId, reason);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener threw exception", e);
            }
        }
    }

    @Override
    public void setMessageSender(Consumer<KeyExchangeMessageData> messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public void addListener(KeyExchangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean removeListener(KeyExchangeListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public void start() {
        running = true;
        LOG.info("GroupKeyExchangeManager started for client " + localClientId);
    }

    @Override
    public void shutdown() {
        running = false;
        pendingDistributions.clear();
        groupMembersCache.clear();
        pendingSessions.clear();
        LOG.info("GroupKeyExchangeManager shutdown");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public SessionKeyManager getSessionManager() {
        return sessionManager;
    }

    @Override
    public int getLocalClientId() {
        return localClientId;
    }

    @Override
    public void rotateGroupKey(int groupId) throws KeyExchangeException {
        if (!running) {
            throw new KeyExchangeException("Manager not running", KeyExchangeException.ErrorCode.INTERNAL_ERROR);
        }

        GroupInfo group = groupRepository.findById(groupId);
        if (group == null) {
            throw new KeyExchangeException("Group not found: " + groupId, KeyExchangeException.ErrorCode.INVALID_PEER_ID, groupId);
        }

        if (group.getAdminId() != localClientId) {
            LOG.warning("Non-admin attempted to rotate group key: " + localClientId);
            return;
        }

        LOG.info("Rotating key for group " + groupId);
        initiateGroupKeyDistribution(groupId, group);
    }

    @Override
    public void handleGroupMemberChange(int groupId, int memberId, boolean isAddition) {
        if (!running) return;

        GroupInfo group = groupRepository.findById(groupId);
        if (group == null || group.getAdminId() != localClientId) {
            return;
        }

        try {
            if (isAddition) {
                LOG.info(String.format("Member %d added to group %d, rotating key", memberId, groupId));
            } else {
                LOG.info(String.format("Member %d removed from group %d, rotating key", memberId, groupId));
            }

            rotateGroupKey(groupId);

        } catch (KeyExchangeException e) {
            LOG.log(Level.SEVERE, "Failed to rotate key after member change", e);
        }
    }

    // ========================= Private Methods =========================

    /**
     * Initiates group key distribution, establishing sessions first if needed.
     */
    private void initiateGroupKeyDistribution(int groupId, GroupInfo group) throws KeyExchangeException {
        // Generate new group key
        SecretKey groupKey = generateGroupKey();

        // Store locally
        String groupConversation = GROUP_PREFIX + groupId;
        sessionManager.storeSessionKey(groupConversation, groupKey);

        // Update cache
        updateGroupMembersCache(groupId, group);

        // Get members (excluding self)
        Set<Integer> members = new HashSet<>(group.getMembersId());
        members.remove(localClientId);

        if (members.isEmpty()) {
            LOG.info("No other members in group " + groupId);
            return;
        }

        // Create pending distribution tracker
        PendingGroupKeyDistribution pending = new PendingGroupKeyDistribution(groupId, members);
        pendingDistributions.put(groupId, pending);

        LOG.info(String.format("Distributing group key for group %d to %d members", groupId, members.size()));

        // Establish sessions with members who don't have one
        establishSessionsAndDistribute(groupId, groupKey, members, pending);
    }

    /**
     * Establishes sessions with members if needed, then distributes the group key.
     */
    private void establishSessionsAndDistribute(int groupId, SecretKey groupKey,
                                                Set<Integer> members,
                                                PendingGroupKeyDistribution pending) {
        for (int memberId : members) {
            String memberConversation = PRIVATE_PREFIX + memberId;

            if (sessionManager.hasSession(memberConversation)) {
                // Session exists, send group key immediately
                try {
                    sendGroupKeyToMember(memberId, groupId, groupKey);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, String.format("Failed to send group key to member %d", memberId), e);
                    pending.markMemberFailed(memberId);
                }
            } else {
                // Need to establish session first
                LOG.info(String.format("Establishing session with member %d for group %d", memberId, groupId));

                establishSessionThenSendGroupKey(memberId, groupId, groupKey, pending);
            }
        }

        notifyGroupKeyDistributionStarted(groupId, members.size());
    }

    /**
     * Establishes a private session with a member, then sends the group key.
     */
    private void establishSessionThenSendGroupKey(int memberId, int groupId,
                                                  SecretKey groupKey,
                                                  PendingGroupKeyDistribution pending) {
        if (privateManager == null) {
            LOG.severe("Private manager not set, cannot establish session");
            pending.markMemberFailed(memberId);
            return;
        }

        // Check if we're already establishing a session
        CompletableFuture<Boolean> existingFuture = pendingSessions.get(memberId);
        if (existingFuture != null && !existingFuture.isDone()) {
            // Wait for existing session establishment
            existingFuture.thenAccept(success -> {
                if (success) {
                    try {
                        sendGroupKeyToMember(memberId, groupId, groupKey);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to send group key after session", e);
                        pending.markMemberFailed(memberId);
                    }
                } else {
                    LOG.warning("Session establishment failed with member " + memberId);
                    pending.markMemberFailed(memberId);
                }
            });
            return;
        }

        // Create new session establishment
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingSessions.put(memberId, future);

        // Set timeout
        future.orTimeout(SESSION_ESTABLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // Add temporary listener for session completion
        KeyExchangeListener tempListener = new KeyExchangeListener() {
            @Override
            public void onKeyExchangeCompleted(int peerId, SecretKey sessionKey) {
                if (peerId == memberId) {
                    future.complete(true);
                    privateManager.removeListener(this);
                }
            }

            @Override
            public void onKeyExchangeFailed(int peerId, KeyExchangeException error) {
                if (peerId == memberId) {
                    future.complete(false);
                    privateManager.removeListener(this);
                }
            }
        };

        privateManager.addListener(tempListener);

        // Initiate private key exchange
        try {
            privateManager.initiateKeyExchange(memberId);

            // Handle completion
            future.thenAccept(success -> {
                pendingSessions.remove(memberId);
                if (success) {
                    LOG.info(String.format("Session established with member %d", memberId));
                    try {
                        sendGroupKeyToMember(memberId, groupId, groupKey);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to send group key", e);
                        pending.markMemberFailed(memberId);
                    }
                } else {
                    LOG.warning(String.format("Failed to establish session with member %d", memberId));
                    pending.markMemberFailed(memberId);
                }
            }).exceptionally(ex -> {
                LOG.log(Level.WARNING, "Session establishment timed out", ex);
                pending.markMemberFailed(memberId);
                pendingSessions.remove(memberId);
                privateManager.removeListener(tempListener);
                return null;
            });

        } catch (KeyExchangeException e) {
            LOG.log(Level.WARNING, "Failed to initiate session", e);
            future.complete(false);
            pending.markMemberFailed(memberId);
            pendingSessions.remove(memberId);
        }
    }

    /**
     * Sends the group key to a specific member.
     */
    private void sendGroupKeyToMember(int memberId, int groupId, SecretKey groupKey) throws GeneralSecurityException {
        String memberConversation = PRIVATE_PREFIX + memberId;
        SecretKey memberSession = sessionManager.getSessionKey(memberConversation);

        if (memberSession == null) {
            throw new GeneralSecurityException("No session with member " + memberId + " for group " + groupId);
        }

        // Encrypt group key with member's session key
        byte[] encryptedGroupKey = encryptGroupKey(groupKey.getEncoded(), memberSession);

        // Create group key exchange data
        byte[] data = GroupKeyExchangeData.encode(groupId, encryptedGroupKey, false);

        // Send via KEY_EXCHANGE message
        sendKeyExchangeMessage(memberId, data);

        LOG.info(String.format("Sent group key for group %d to member %d", groupId, memberId));
    }

    private void sendGroupKeyAck(int adminId, int groupId) {
        byte[] data = GroupKeyExchangeData.encodeAck(groupId);
        sendKeyExchangeResponseMessage(adminId, data);
    }

    /**
     * Encrypts a group key using AES-GCM with the member's session key.
     * <p>
     * Uses a fresh random nonce for each encryption. The nonce is prepended
     * to the ciphertext for transmission.
     *
     * @param groupKey    the group key bytes to encrypt
     * @param sessionKey  the member's session key
     * @return nonce + ciphertext (12 bytes nonce + encrypted data with auth tag)
     * @throws GeneralSecurityException if encryption fails
     */
    private byte[] encryptGroupKey(byte[] groupKey, SecretKey sessionKey) throws GeneralSecurityException {
        // Generate fresh nonce for this encryption
        byte[] nonce = cipher.generateNonce();

        // Encrypt the group key with AES-GCM (includes authentication tag)
        byte[] ciphertext = cipher.encrypt(groupKey, sessionKey, nonce, null);

        // Prepend nonce to ciphertext for transmission
        byte[] result = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, result, 0, nonce.length);
        System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);

        return result;
    }

    /**
     * Decrypts a group key using AES-GCM with the admin's session key.
     * <p>
     * Expects the data to be: nonce (12 bytes) + ciphertext (with auth tag).
     *
     * @param encryptedData  nonce + ciphertext received from admin
     * @param sessionKey     the admin's session key
     * @return the decrypted group key bytes
     * @throws GeneralSecurityException if decryption or authentication fails
     */
    private byte[] decryptGroupKey(byte[] encryptedData, SecretKey sessionKey)
            throws GeneralSecurityException {

        int nonceLength = cipher.getNonceLength();

        if (encryptedData.length < nonceLength) {
            throw new GeneralSecurityException("Encrypted data too short - missing nonce");
        }

        // Extract nonce from the beginning
        byte[] nonce = new byte[nonceLength];
        System.arraycopy(encryptedData, 0, nonce, 0, nonceLength);

        // Extract ciphertext (everything after nonce)
        byte[] ciphertext = new byte[encryptedData.length - nonceLength];
        System.arraycopy(encryptedData, nonceLength, ciphertext, 0, ciphertext.length);

        // Decrypt and verify authentication tag
        return cipher.decrypt(ciphertext, sessionKey, nonce, null);
    }

    private SecretKey generateGroupKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, secureRandom);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES not available", e);
        }
    }

    private void updateGroupMembersCache(int groupId, GroupInfo group) {
        groupMembersCache.put(groupId, new HashSet<>(group.getMembersId()));
    }

    private void sendKeyExchangeMessage(int peerId, byte[] data) {
        Consumer<KeyExchangeMessageData> sender = this.messageSender;
        if (sender == null) {
            LOG.warning("No message sender configured");
            return;
        }

        KeyExchangeMessageData msgData = new KeyExchangeMessageData(localClientId, peerId, data, false);
        sender.accept(msgData);
    }

    private void sendKeyExchangeResponseMessage(int peerId, byte[] data) {
        Consumer<KeyExchangeMessageData> sender = this.messageSender;
        if (sender == null) {
            LOG.warning("No message sender configured");
            return;
        }

        KeyExchangeMessageData msgData = new KeyExchangeMessageData(localClientId, peerId, data, true);
        sender.accept(msgData);
    }

    // ========================= Listener Notifications =========================

    private void notifyGroupKeyReceived(int groupId, int adminId) {
        for (KeyExchangeListener listener : listeners) {
            try {
                listener.onKeyExchangeCompleted(groupId, sessionManager.getSessionKey(GROUP_PREFIX + groupId));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener threw exception", e);
            }
        }
    }

    private void notifyGroupKeyDistributionStarted(int groupId, int memberCount) {
        LOG.info(String.format("Starting group key distribution for group %d to %d members", groupId, memberCount));
    }

    private void notifyGroupKeyDistributionComplete(int groupId) {
        LOG.info("Group key distribution complete for group " + groupId);
    }

    // ========================= Inner Classes =========================

    private static class PendingGroupKeyDistribution {
        private final int groupId;
        private final Set<Integer> pendingMembers;
        private final Set<Integer> completedMembers;
        private final Set<Integer> failedMembers;

        PendingGroupKeyDistribution(int groupId, Set<Integer> members) {
            this.groupId = groupId;
            this.pendingMembers = new HashSet<>(members);
            this.completedMembers = new HashSet<>();
            this.failedMembers = new HashSet<>();
        }

        synchronized void markMemberComplete(int memberId) {
            pendingMembers.remove(memberId);
            completedMembers.add(memberId);
        }

        synchronized void markMemberFailed(int memberId) {
            pendingMembers.remove(memberId);
            failedMembers.add(memberId);
        }

        synchronized boolean isComplete() {
            return pendingMembers.isEmpty();
        }
    }
}