package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.Packet;

import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * Protocol message for server-client ECDH key exchange.
 * <p>
 * Used for both directions:
 * <ul>
 *   <li>{@link MessageType#SERVER_KEY_EXCHANGE}: Server sends its public key to client</li>
 *   <li>{@link MessageType#SERVER_KEY_EXCHANGE_RESPONSE}: Client responds with its public key</li>
 * </ul>
 * <pre>
 * [4 bytes: public key length] [N bytes: public key]
 * </pre>
 */
public class ServerKeyExchangeMessage extends ProtocolMessage {

    private byte[] publicKey;

    /**
     * Default constructor for deserialization.
     */
    public ServerKeyExchangeMessage() {
        super(MessageType.SERVER_KEY_EXCHANGE, 0, 0);
        this.publicKey = new byte[0];
    }

    /**
     * Creates a new ServerKeyExchangeMessage.
     *
     * @param messageType the message type (SERVER_KEY_EXCHANGE or SERVER_KEY_EXCHANGE_RESPONSE)
     * @param from        the sender ID (0 for server)
     * @param to          the recipient ID (0 for server)
     */
    public ServerKeyExchangeMessage(MessageType messageType, int from, int to) {
        super(messageType, from, to);
        this.publicKey = new byte[0];
    }

    // ========================= Factory Methods =========================

    /**
     * Creates a SERVER_KEY_EXCHANGE message (server to client).
     *
     * @param clientId  the target client ID
     * @param publicKey the server's public key
     * @return a new ServerKeyExchangeMessage
     */
    public static ServerKeyExchangeMessage createServerHello(int clientId, byte[] publicKey) {
        ServerKeyExchangeMessage msg = new ServerKeyExchangeMessage(
                MessageType.SERVER_KEY_EXCHANGE, 0, clientId);
        msg.setPublicKey(publicKey);
        return msg;
    }

    /**
     * Creates a SERVER_KEY_EXCHANGE message from a PublicKey object.
     *
     * @param clientId  the target client ID
     * @param publicKey the server's public key
     * @return a new ServerKeyExchangeMessage
     */
    public static ServerKeyExchangeMessage createServerHello(int clientId, PublicKey publicKey) {
        return createServerHello(clientId, publicKey.getEncoded());
    }

    /**
     * Creates a SERVER_KEY_EXCHANGE_RESPONSE message (client to server).
     *
     * @param clientId  the client's ID (can be 0 for new clients)
     * @param publicKey the client's public key
     * @return a new ServerKeyExchangeMessage
     */
    public static ServerKeyExchangeMessage createClientResponse(int clientId, byte[] publicKey) {
        ServerKeyExchangeMessage msg = new ServerKeyExchangeMessage(
                MessageType.SERVER_KEY_EXCHANGE_RESPONSE, clientId, 0);
        msg.setPublicKey(publicKey);
        return msg;
    }

    /**
     * Creates a SERVER_KEY_EXCHANGE_RESPONSE message from a PublicKey object.
     *
     * @param clientId  the client's ID (can be 0 for new clients)
     * @param publicKey the client's public key
     * @return a new ServerKeyExchangeMessage
     */
    public static ServerKeyExchangeMessage createClientResponse(int clientId, PublicKey publicKey) {
        return createClientResponse(clientId, publicKey.getEncoded());
    }

    // ========================= Getters/Setters =========================

    /**
     * Gets the public key bytes.
     *
     * @return a copy of the public key bytes
     */
    public byte[] getPublicKey() {
        return publicKey != null ? publicKey.clone() : null;
    }

    /**
     * Sets the public key bytes.
     *
     * @param publicKey the public key bytes
     * @return this message for chaining
     * @throws IllegalArgumentException if publicKey is null or empty
     */
    public ServerKeyExchangeMessage setPublicKey(byte[] publicKey) {
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalArgumentException("Public key cannot be null or empty");
        }
        this.publicKey = publicKey.clone();
        return this;
    }

    /**
     * Sets the public key from a PublicKey object.
     *
     * @param publicKey the public key
     * @return this message for chaining
     */
    public ServerKeyExchangeMessage setPublicKey(PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("Public key cannot be null");
        }
        return setPublicKey(publicKey.getEncoded());
    }

    // ========================= Serialization =========================

    @Override
    public Packet toPacket() {
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalStateException("Public key must be set before serialization");
        }

        // Format: [4 bytes: key length] [N bytes: key data]
        int payloadSize = Integer.BYTES + publicKey.length;
        byte[] payload = new byte[payloadSize];
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.putInt(publicKey.length);
        buffer.put(publicKey);

        return new Packet.PacketBuilder(payloadSize)
                .setMessageType(messageType)
                .setFrom(from)
                .setTo(to)
                .setPayload(payload)
                .build();
    }

    @Override
    public ProtocolMessage fromPacket(Packet packet) {
        this.messageType = packet.messageType();
        this.from = packet.from();
        this.to = packet.to();

        ByteBuffer buffer = packet.getPayload();
        int keyLength = buffer.getInt();

        if (keyLength <= 0 || keyLength > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid public key length: " + keyLength);
        }

        this.publicKey = new byte[keyLength];
        buffer.get(this.publicKey);

        return this;
    }

    // ========================= Utility =========================

    @Override
    public String toString() {
        return String.format("ServerKeyExchangeMessage{type=%s, from=%d, to=%d, keyLength=%d}",
                messageType, from, to, publicKey != null ? publicKey.length : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerKeyExchangeMessage that)) return false;
        return from == that.from &&
                to == that.to &&
                messageType == that.messageType &&
                Arrays.equals(publicKey, that.publicKey);
    }

    @Override
    public int hashCode() {
        int result = messageType.hashCode();
        result = 31 * result + from;
        result = 31 * result + to;
        result = 31 * result + Arrays.hashCode(publicKey);
        return result;
    }
}
