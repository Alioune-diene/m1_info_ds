package fr.uga.im2ag.m1info.client.model;

import fr.uga.im2ag.m1info.common.messagefactory.TextMessage;
import fr.uga.im2ag.m1info.common.model.GroupInfo;
import fr.uga.im2ag.m1info.common.repository.GroupRepository;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

public class ConversationClient implements Serializable{
    private final String conversationId;
    private String conversationName;
    private final Map<String, Message> messages;
    private final SortedMap<Instant, String> messageOrder; // Red Black Tree, O(log n) for get, put, remove
    //private final Set<Integer> participantIds;
    private final int peerId;
    private final boolean isGroupConversation;
    private GroupInfo group;
    private ContactClient other;

    public ConversationClient(String conversationId, String conversationName, Map<String, Message> messages, SortedMap<Instant, String> messageOrder, int peerId, boolean isGroupConversation) {
        this.conversationId = conversationId;
        this.conversationName = conversationName;
        this.messages = messages;
        this.messageOrder = messageOrder;
        this.peerId = peerId;
        this.isGroupConversation = isGroupConversation;
        this.group = null;
    }

    public ConversationClient(String conversationId, ContactClient user, int peerId, boolean isGroupConversation) {
        this(conversationId, "Conversation avec " + user.getPseudo(), new HashMap<>(), new TreeMap<>(), peerId, isGroupConversation);
        other = user;
    }

    public ConversationClient(String conversationId, int peerId, boolean isGroupConversation, GroupInfo group) {
        this(conversationId, "Conversation " + conversationId, new HashMap<>(), new TreeMap<>(), peerId, isGroupConversation);
        this.group = group;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getConversationName() {
        if (isGroupConversation() && group != null){
            return group.getGroupName();
        }
        return other.getPseudo();
    }

    public void setConversationName(String conversationName) {
        this.conversationName = conversationName;
    }

    public void addMessage(Message message) {
        messages.put(message.getMessageId(), message);
        messageOrder.put(message.getTimestamp(), message.getMessageId());
    }

    public void addMessage(TextMessage message){
        Message msg = new Message (message.getMessageId(),
                                           message.getFrom(),
                                           message.getTo(),
                                           message.getContent(),
                                           message.getTimestamp(),
                                           null);

        this.addMessage(msg);
    }

    public void removeMessage(String messageId) {
        Message message = messages.remove(messageId);
        if (message != null) {
            messageOrder.remove(message.getTimestamp());
        }
    }

    public Message getMessage(String messageId) {
        return messages.get(messageId);
    }

    /** Get a sublist of n (all if n is -1) messages, ordered by timestamp,
     * from the message with id startMessageId (inclusive if specified),
     * or from the beginning if startMessageId is null.
     *
     * @param startMessageId the id of the message to start from (inclusive), or null to start from the beginning
     * @param n the number of messages to retrieve, or -1 to retrieve all messages
     * @param inclusive whether to include the startMessageId message
     * @param ascending whether to return messages in ascending order (oldest first) or descending order (newest first)
     * @return a list of messages
     */
    public List<Message> getMessagesFrom(String startMessageId, int n, boolean inclusive, boolean ascending) {
        List<Message> result = new ArrayList<>();
        boolean startAdding = (startMessageId == null);
        for (Map.Entry<Instant, String> entry : messageOrder.entrySet()) {
            String messageId = entry.getValue();
            if (!startAdding) {
                if (messageId.equals(startMessageId)) {
                    startAdding = true;
                    if (inclusive) {
                        result.add(messages.get(messageId));
                    }
                }
            } else {
                if (n != -1 && result.size() >= n) {
                    break;
                }
                result.add(messages.get(messageId));
            }
        }
        return ascending ? result : result.reversed();
    }

    public int getPeerId() {
        return peerId;
    }

    /**
     * @deprecated Use GroupRepository to get participant IDs for group conversations.
     */
    @Deprecated
    public Set<Integer> getParticipantIds(GroupRepository groupRepository) {
        if (isGroupConversation) {
            GroupInfo groupInfo = groupRepository.findById(peerId);
            if (groupInfo != null) {
                return Set.copyOf(groupInfo.getMembersId());
            }
            return Set.of();
        } else {
            return Set.of(peerId);
        }
    }

    /**
     * @deprecated Use GroupRepository to get participant list for group conversations.
     * Can be used only for group conversations.
     */
    @Deprecated
    public Map<Integer, String> getParticipantList(GroupRepository groupRepository) {
        if (isGroupConversation) {
            GroupInfo groupInfo = groupRepository.findById(peerId);
            if (groupInfo != null) {
                return groupInfo.getMembers();
            }
        }
        return Map.of();
    }

    public boolean isGroupConversation() {
        return isGroupConversation;
    }

    public Message getLastMessage() {
        // TODO: Use getMessagesFrom ?
        if (messageOrder.isEmpty()) {
            return null;
        }
        Instant lastKey = messageOrder.lastKey();
        String lastMessageId = messageOrder.get(lastKey);
        return messages.get(lastMessageId);
    }

    public void addReactionToMessage(String messageId, String reaction, int userId) {
        Message message = messages.get(messageId);
        if (message != null) {
            message.addReaction(reaction, userId);
        }
    }
}
