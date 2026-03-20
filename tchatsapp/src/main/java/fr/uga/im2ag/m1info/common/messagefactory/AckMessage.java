package fr.uga.im2ag.m1info.common.messagefactory;

import com.google.gson.Gson;
import fr.uga.im2ag.m1info.common.MessageStatus;
import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.TypeConverter;

import java.io.Serial;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Message representing an acknowledgment for another message.
 * Used to track message delivery and read status.
 */
public class AckMessage extends ProtocolMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Gson gson = new Gson();

    private String acknowledgedMessageId;
    private MessageStatus ackType;
    private String errorReason;
    private final Map<String, Object> additionalData;

    /**
     * Constructor for AckMessage.
     *
     * @param from the sender ID
     * @param to the recipient ID
     */
    public AckMessage(int from, int to) {
        super(MessageType.MESSAGE_ACK, from, to);
        this.acknowledgedMessageId = "";
        this.ackType = MessageStatus.SENT;
        this.errorReason = null;
        this.additionalData = new HashMap<>();
    }

    // ========================= Getters/Setters =========================

    /**
     * Get the ID of the message being acknowledged.
     *
     * @return the acknowledged message ID
     */
    public String getAcknowledgedMessageId() {
        return acknowledgedMessageId;
    }

    /**
     * Set the ID of the message being acknowledged.
     *
     * @param acknowledgedMessageId the acknowledged message ID
     * @return this AckMessage for chaining
     */
    public AckMessage setAcknowledgedMessageId(String acknowledgedMessageId) {
        this.acknowledgedMessageId = acknowledgedMessageId;
        return this;
    }

    /**
     * Get the acknowledgment type.
     *
     * @return the acknowledgment type
     */
    public MessageStatus getAckType() {
        return ackType;
    }

    /**
     * Set the acknowledgment type.
     *
     * @param ackType the acknowledgment type
     * @return this AckMessage for chaining
     */
    public AckMessage setAckType(MessageStatus ackType) {
        this.ackType = ackType;
        return this;
    }

    /**
     * Get the error reason.
     *
     * @return the error reason, or null if no error
     */
    public String getErrorReason() {
        return errorReason;
    }

    /**
     * Set the error reason.
     *
     * @param errorReason the error reason
     * @return this AckMessage for chaining
     */
    public AckMessage setErrorReason(String errorReason) {
        this.errorReason = errorReason;
        return this;
    }

    /** Get a parameter by key.
     *
     * @param key the parameter key
     * @return the parameter value
     */
    public Object getParam(String key) {
        return additionalData.get(key);
    }

    /** Get a parameter by key and cast it to the specified type.
     *
     * @param key the parameter key
     * @param clazz the class of the type to cast to
     * @param <T> the type to cast to
     * @return the parameter value cast to the specified type, or null if not found or cannot be cast
     */
    public <T> T getParamAsType(String key, Class<T> clazz) {
        return TypeConverter.convert(additionalData.get(key), clazz);
    }

    /** Add a parameter to the message.
     *
     * @param key the parameter key
     * @param value the parameter value
     * @return the ManagementMessage instance (for chaining)
     */
    public AckMessage addParam(String key, Object value) {
        additionalData.put(key, value);
        return this;
    }

    /** Remove a parameter from the message.
     *
     * @param key the parameter key
     * @return the ManagementMessage instance (for chaining)
     */
    public AckMessage removeParam(String key) {
        additionalData.remove(key);
        return this;
    }

    /**
     * Get all additional data as a map.
     *
     * @return the additional data map
     */
    public Map<String, Object> getAdditionalData() {
        return Map.copyOf(additionalData);
    }
}