package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

import fr.uga.im2ag.m1info.tchatsapp.common.repository.GroupRepository;
import fr.uga.im2ag.m1info.tchatsapp.crypto.SessionKeyManager;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class CompositeKeyExchangeManager implements IKeyExchangeManager {

    private static final Logger LOG = Logger.getLogger(CompositeKeyExchangeManager.class.getName());

    // ========================= Dependencies =========================

    private final PrivateKeyExchangeManager privateManager;
    private final GroupKeyExchangeManager groupManager;
    private final GroupRepository groupRepository;
    private final int localClientId;

    // ========================= Constructor =========================

    /**
     * Creates a new CompositeKeyExchangeManager with proper interconnection.
     */
    public CompositeKeyExchangeManager(
            PrivateKeyExchangeManager privateManager,
            GroupKeyExchangeManager groupManager,
            GroupRepository groupRepository) {

        Objects.requireNonNull(privateManager, "Private manager cannot be null");
        Objects.requireNonNull(groupManager, "Group manager cannot be null");
        Objects.requireNonNull(groupRepository, "Group repository cannot be null");

        this.privateManager = privateManager;
        this.groupManager = groupManager;
        this.groupRepository = groupRepository;

        // Verify both managers have the same local client ID
        if (privateManager.getLocalClientId() != groupManager.getLocalClientId()) {
            throw new IllegalArgumentException("Managers must have the same local client ID");
        }

        this.localClientId = privateManager.getLocalClientId();

        groupManager.setPrivateManager(privateManager);
    }

    // ========================= IKeyExchangeManager Implementation =========================

    @Override
    public boolean canHandle(int conversationId) {
        return true;
    }

    @Override
    public void initiateKeyExchange(int targetId) throws KeyExchangeException {
        IKeyExchangeManager manager = selectManager(targetId);
        LOG.info(String.format("Initiating key exchange for %d using %s", targetId, manager.getClass().getSimpleName()));
        manager.initiateKeyExchange(targetId);
    }

    @Override
    public void handleKeyExchangeRequest(int peerId, byte[] publicKeyBytes) throws KeyExchangeException {
        if (GroupKeyExchangeData.isGroupKeyExchange(publicKeyBytes)) {
            LOG.fine("Routing KEY_EXCHANGE to group manager");
            groupManager.handleKeyExchangeRequest(peerId, publicKeyBytes);
        } else {
            LOG.fine("Routing KEY_EXCHANGE to private manager");
            privateManager.handleKeyExchangeRequest(peerId, publicKeyBytes);
        }
    }

    @Override
    public void handleKeyExchangeResponse(int peerId, byte[] publicKeyBytes) throws KeyExchangeException {
        if (GroupKeyExchangeData.isGroupKeyAck(publicKeyBytes)) {
            LOG.fine("Routing KEY_EXCHANGE_RESPONSE to group manager (ACK)");
            groupManager.handleKeyExchangeResponse(peerId, publicKeyBytes);
        } else {
            LOG.fine("Routing KEY_EXCHANGE_RESPONSE to private manager");
            privateManager.handleKeyExchangeResponse(peerId, publicKeyBytes);
        }
    }

    @Override
    public boolean hasSessionWith(int targetId) {
        IKeyExchangeManager manager = selectManager(targetId);
        boolean hasSession = manager.hasSessionWith(targetId);
        LOG.fine(String.format("Checking session with %d (%s): %s", targetId, isGroup(targetId) ? "group" : "private", hasSession));
        return hasSession;
    }

    @Override
    public void invalidateSession(int targetId) {
        IKeyExchangeManager manager = selectManager(targetId);
        manager.invalidateSession(targetId);
    }

    @Override
    public void invalidateSession(int targetId, String reason) {
        IKeyExchangeManager manager = selectManager(targetId);
        manager.invalidateSession(targetId, reason);
    }

    @Override
    public void setMessageSender(Consumer<KeyExchangeMessageData> messageSender) {
        privateManager.setMessageSender(messageSender);
        groupManager.setMessageSender(messageSender);
        LOG.fine("Message sender configured for both managers");
    }

    @Override
    public void addListener(KeyExchangeListener listener) {
        privateManager.addListener(listener);
        groupManager.addListener(listener);
    }

    @Override
    public boolean removeListener(KeyExchangeListener listener) {
        boolean removedPrivate = privateManager.removeListener(listener);
        boolean removedGroup = groupManager.removeListener(listener);
        return removedPrivate || removedGroup;
    }

    @Override
    public void start() {
        privateManager.start();
        groupManager.start();
        LOG.info("CompositeKeyExchangeManager started for client " + localClientId);
    }

    @Override
    public void shutdown() {
        privateManager.shutdown();
        groupManager.shutdown();
        LOG.info("CompositeKeyExchangeManager shutdown");
    }

    @Override
    public boolean isRunning() {
        return privateManager.isRunning() && groupManager.isRunning();
    }

    @Override
    public SessionKeyManager getSessionManager() {
        return privateManager.getSessionManager();
    }

    @Override
    public int getLocalClientId() {
        return localClientId;
    }

    @Override
    public void rotateGroupKey(int groupId) throws KeyExchangeException {
        if (!isGroup(groupId)) {
            LOG.warning("Attempted to rotate key for non-group: " + groupId);
            throw new IllegalArgumentException("Not a group ID: " + groupId);
        }

        LOG.info("Rotating group key for group " + groupId);
        groupManager.rotateGroupKey(groupId);
    }

    @Override
    public void handleGroupMemberChange(int groupId, int memberId, boolean isAddition) {
        if (!isGroup(groupId)) {
            LOG.warning(String.format("Member change for non-group ID %d", groupId));
            return;
        }
        LOG.info(String.format("Handling %s of member %d for group %d", isAddition ? "addition" : "removal", memberId, groupId));
        groupManager.handleGroupMemberChange(groupId, memberId, isAddition);
    }

    // ========================= Public Utility Methods =========================

    /**
     * Gets the private key exchange manager.
     */
    public PrivateKeyExchangeManager getPrivateManager() {
        return privateManager;
    }

    /**
     * Gets the group key exchange manager.
     */
    public GroupKeyExchangeManager getGroupManager() {
        return groupManager;
    }

    /**
     * Checks if an ID represents a group.
     */
    public boolean isGroup(int id) {
        boolean isGroup = groupRepository.findById(id) != null;
        LOG.finest(String.format("ID %d is %s", id, isGroup ? "a group" : "not a group"));
        return isGroup;
    }

    /**
     * Checks if a session exists (either private or group).
     */
    public boolean hasSession(String conversationId) {
        boolean hasSession = getSessionManager().hasSession(conversationId);
        LOG.fine(String.format("Session check for %s: %s", conversationId, hasSession));
        return hasSession;
    }

    /**
     * Triggers a key exchange for a target (group or private).
     * This is useful for ensuring encryption is ready before sending.
     */
    public void ensureSessionExists(int targetId) throws KeyExchangeException {
        if (!hasSessionWith(targetId)) {
            LOG.info(String.format("No session with %d, initiating key exchange", targetId));
            initiateKeyExchange(targetId);
        }
    }

    // ========================= Private Helper Methods =========================

    /**
     * Selects the appropriate manager for a target ID.
     */
    private IKeyExchangeManager selectManager(int targetId) {
        if (isGroup(targetId)) {
            LOG.finest("Selected GroupKeyExchangeManager for ID " + targetId);
            return groupManager;
        } else {
            LOG.finest("Selected PrivateKeyExchangeManager for ID " + targetId);
            return privateManager;
        }
    }
}