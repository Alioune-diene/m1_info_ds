package fr.uga.im2ag.m1info.tchatsapp.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents a packet sent between the client and the server (or vice versa)
 * It contains a ByteBuffer where the first 4 bytes are the length (in bytes) of the payload (i.e. the content),
 * then 4 bytes for the sender id, 4 bytes for the recipient id and then the payload.
 */
public class Packet {

    private static final int OFFSET_LENGTH = 0;
    private static final int OFFSET_TYPE   = OFFSET_LENGTH + Integer.BYTES;
    private static final int OFFSET_FROM   = OFFSET_TYPE + Integer.BYTES;
    private static final int OFFSET_TO     = OFFSET_FROM + Integer.BYTES;
    private final static int HEADER_SIZE = 4 * Integer.BYTES;

    public static int getHeaderSize() {
        return HEADER_SIZE;
    }

    /**
     * A builder for packets.
     * */
    public static final class PacketBuilder {

        private ByteBuffer buf;

        /** Creates a new PacketBuilder with a specified payload size.
         *
         * @param payloadSize the size of the payload in bytes
         */
        public PacketBuilder(int payloadSize) {
            buf = ByteBuffer.allocate(HEADER_SIZE+payloadSize);
            buf.putInt(payloadSize);
        }

        /** Resets the PacketBuilder with a new payload size.
         *
         * @param payloadSize the size of the payload in bytes
         * @return the PacketBuilder instance for method chaining
         */
        public PacketBuilder reset(int payloadSize) {
            buf = ByteBuffer.allocate(HEADER_SIZE+payloadSize);
            buf.putInt(payloadSize);
            return this;
        }

        /** Returns a ByteBuffer representing the payload of the packet.
         *
         * @return a ByteBuffer containing the payload data
         * @throws IllegalStateException if the PacketBuilder has not been initialized
         */
        public ByteBuffer getPayload() {
            return  buf.slice(HEADER_SIZE,buf.capacity());
        }

        /** Sets the sender ID for the packet.
         *
         * @param from the sender ID
         * @return the PacketBuilder instance for method chaining
         */
        public PacketBuilder setFrom(int from) {
            buf.putInt(OFFSET_FROM,from);
            buf.position(HEADER_SIZE);
            return this;
        }

        /** Sets the recipient ID for the packet.
         *
         * @param to the recipient ID
         * @return the PacketBuilder instance for method chaining
         */
        public PacketBuilder setTo(int to) {
            buf.putInt(OFFSET_TO,to);
            buf.position(HEADER_SIZE);
            return this;
        }

        /** Sets the message type for the packet.
         *
         * @param messageType the message type
         * @return the PacketBuilder instance for method chaining
         */
        public PacketBuilder setMessageType(MessageType messageType) {
            buf.putInt(OFFSET_TYPE, messageType.toByte());
            buf.position(HEADER_SIZE);
            return this;
        }

        /** Sets the payload for the packet.
         *
         * @param payload the payload data
         * @return the PacketBuilder instance for method chaining
         * @throws IllegalArgumentException if the payload size does not match the expected size
         */
        public PacketBuilder setPayload(byte[] payload) throws IllegalArgumentException {
            if (payload.length<buf.capacity()-HEADER_SIZE) {
                throw new IllegalArgumentException("payload is of length "+payload.length+" but payload is of length "+(buf.capacity()-HEADER_SIZE));
            }
            buf.position(HEADER_SIZE);
            buf.put(payload);
            return this;
        }

        /** Checks if the packet building is completed.
         *
         * @return true if the packet is fully built, false otherwise
         * @throws IllegalStateException if the PacketBuilder has not been initialized
         */
        public boolean isCompleted() throws IllegalStateException {
            if (buf==null) throw new IllegalStateException("reset method has to be called before");
            return !buf.hasRemaining();
        }

        /** Checks if the PacketBuilder is ready to build a packet.
         *
         * @return true if the PacketBuilder is initialized and not yet completed, false otherwise
         */
        public boolean isReady() {
            return buf!=null && !isCompleted();
        }

        /** Fills the packet payload from the given ByteBuffer.
         *
         * @param bf the ByteBuffer containing the data to fill
         * @return the PacketBuilder instance for method chaining
         * @throws IllegalStateException if the PacketBuilder has not been initialized
         */
        public PacketBuilder fillFrom(ByteBuffer bf) throws IllegalStateException {
            if (buf==null) throw new IllegalStateException("reset method has to be called before");
            int length = Math.min(buf.remaining(),bf.remaining());
            buf.put(buf.position(),bf,bf.position(),length);
            buf.position(buf.position()+length);
            bf.position(bf.position()+length);
            //return !buf.hasRemaining();
            return this;
        }

        /** Builds the packet if it is completed.
         *
         * @return the constructed Packet
         * @throws RuntimeException if the packet is not yet finished
         */
        public Packet build() {
            if (isCompleted()) {
                buf.position(0);
                Packet res=new Packet(buf);
                buf=null;
                return res;
            }
            throw new RuntimeException("Packet not finished..."); // could have computed automatically the size...
        }
    }

    // ORDER : TYPE(4) | LENGTH(4) | FROM(4) | TO(4) | PAYLOAD(LENGTH)
    private final ByteBuffer buffer;

    /** Private constructor to create a Packet from a ByteBuffer.
     *
     * @param buf the ByteBuffer containing the packet data
     */
    private Packet(ByteBuffer buf) {
        buffer = buf.rewind().asReadOnlyBuffer();
    }

    /** Returns the sender ID of the packet.
     *
     * @return the sender ID
     */
    public int from() {
        return buffer.getInt(OFFSET_FROM);
    }

    /** Returns the recipient ID of the packet.
     *
     * @return the recipient ID
     */
    public int to() {
        return buffer.getInt(OFFSET_TO);
    }

    /** Returns the size of the payload in bytes.
     *
     * @return the payload size
     */
    public int payloadSize() {
        return buffer.getInt(OFFSET_LENGTH);
    }

    /** Returns the message type of the packet.
     *
     * @return the message type as an integer
     */
    public MessageType messageType() {
        return MessageType.fromInt(buffer.getInt(OFFSET_TYPE));
    }

    /**
     * Returns a readonly view of the payload
     * The position of the buffer is 0.
     *
     * @return a ByteBuffer containing the payload data
     */
    public ByteBuffer getPayload() {
        return buffer.slice(HEADER_SIZE, buffer.getInt(0));
    }

    /** Returns a duplicate modifiable view of the payload.
     * The position of the buffer is 0.
     *
     * @return a ByteBuffer containing the payload data (modifiable)
     */
    public ByteBuffer getModifiablePayload() {
        ByteBuffer modifiableBuffer = ByteBuffer.allocate(payloadSize());
        ByteBuffer payloadSlice = buffer.slice(HEADER_SIZE, payloadSize());
        modifiableBuffer.put(payloadSlice);
        modifiableBuffer.rewind();
        return modifiableBuffer;
    }

    /**
     * Returns a (readonly) view of the whole packet
     *
     * @return a ByteBuffer containing the entire packet data
     */
    public ByteBuffer asByteBuffer() {
        return buffer.duplicate().rewind();
    }

    /** Returns a byte array representation of the entire packet.
     *
     * @return a byte array containing the packet data
     */
    public byte[] asByteArray() {
        byte[] arr = new byte[buffer.capacity()];
        ByteBuffer dup = buffer.duplicate().rewind();
        dup.get(arr);
        return arr;
    }

    /** Reads a Packet from a DataInputStream.
     *
     * @param dis the DataInputStream to read from
     * @return the constructed Packet
     * @throws IOException if an I/O error occurs
     */
    public static Packet readFrom(DataInputStream dis) throws IOException {
        int payloadSize = dis.readInt();
        int messageType = dis.readInt();
        int from = dis.readInt();
        int to = dis.readInt();
        byte[] payload = new byte[payloadSize];
        dis.readFully(payload);
        return new PacketBuilder(payloadSize)
                .setMessageType(MessageType.fromInt(messageType))
                .setFrom(from)
                .setTo(to)
                .setPayload(payload)
                .build();
    }

    /** Writes the Packet to a DataOutputStream.
     *
     * @param dos the DataOutputStream to write to
     * @throws IOException if an I/O error occurs
     */
    public void writeTo(DataOutputStream dos) throws IOException {
        dos.write(asByteArray());
        dos.flush();
    }

    /** Returns a string representation of the Packet.
     *
     * @return a string describing the Packet
     */
    @Override
    public String toString(){
        return "Packet { " +
                "from = " + this.from() + ", " +
                "to = " + this.to() + ", " +
                "messageType = " + this.messageType().name() + ", " +
                "payload size = " + this.payloadSize() +
                " }";
    }
}
