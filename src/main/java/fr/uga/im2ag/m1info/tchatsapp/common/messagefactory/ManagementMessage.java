package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.TypeConverter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Class representing a management message in the chat service protocol.
 */
public class ManagementMessage extends AbstractSerializableMessage {
    private static final Gson gson = new Gson();
    private final Map<String, Object> params;

    /** Default constructor for ManagementMessage with CREATE_USER type.
     * This is used for message factory registration only.
     */
    public ManagementMessage() {
        super(MessageType.NONE, -1, -1);
        params = new java.util.HashMap<>();
    }

    // ========================= Getters/Setters =========================

    /** Get a parameter by key.
     *
     * @param key the parameter key
     * @return the parameter value
     */
    public Object getParam(String key) {
        return params.get(key);
    }

    /** Get a parameter by key and cast it to the specified type.
     *
     * @param key the parameter key
     * @param clazz the class of the type to cast to
     * @param <T> the type to cast to
     * @return the parameter value cast to the specified type, or null if not found or cannot be cast
     */
    public <T> T getParamAsType(String key, Class<T> clazz) {
        return TypeConverter.convert(params.get(key), clazz);
    }

    /** Add a parameter to the message.
     *
     * @param key the parameter key
     * @param value the parameter value
     * @return the ManagementMessage instance (for chaining)
     */
    public ManagementMessage addParam(String key, Object value) {
        params.put(key, value);
        return this;
    }

    /** Remove a parameter from the message.
     *
     * @param key the parameter key
     * @return the ManagementMessage instance (for chaining)
     */
    public ManagementMessage removeParam(String key) {
        params.remove(key);
        return this;
    }

    // ========================= Serialization Methods =========================

    @Override
    protected void serializeContent(StringBuilder sb) {
        sb.append(gson.toJson(params));
    }

    @Override
    protected void deserializeContent(String[] parts, int startIndex) {
        if (parts.length > startIndex && !parts[startIndex].isEmpty()) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> parsed = gson.fromJson(parts[startIndex], type);
            params.clear();
            params.putAll(parsed);
        }
    }

    @Override
    protected int getExpectedPartCount() {
        return 3;
    }

    @Override
    public String toString() {
        return "ManagementMessage{" +
                "params=" + params +
                ", messageType=" + messageType +
                ", from=" + from +
                ", to=" + to +
                ", messageId='" + messageId + '\'' +
                '}';
    }
}
