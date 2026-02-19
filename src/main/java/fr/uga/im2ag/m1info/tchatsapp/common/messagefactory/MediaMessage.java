package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.io.Serial;
import java.util.Arrays;
import java.util.Base64;

/**
 * Class representing a media message in the chat service protocol.
 */
public class MediaMessage extends ProtocolMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private byte[] content;
    private String mediaName;
    private String replyToMessageId;
    private int size;

    /** Default constructor for MediaMessage.
     * This is used for message factory registration only.
     */
    public MediaMessage() {
        super(MessageType.NONE, -1, -1);
        this.mediaName = "";
        this.replyToMessageId = null;
    }

    // ========================= Getters/Setters =========================

    /** Get the media of the message.
     *
     * @return the media in byte representation
     */
    public byte[] getContent() {
        return content;
    }

    /** Get the ID of the message being replied to.
     *
     * @return the reply-to message ID, or null if not a reply
     */
    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    /** Get the name of the media of the message.
     *
     * @return the name of the media send in this packet
     */
    public String getMediaName() {
        return mediaName;
    }

    /** Set the media content of the message.
     *
     * @param content the media content to set
     */
    public void setContent(byte[] content) {
        this.content = content != null ? content.clone() : null;
    }

    /** Set the size of the content.
     *
     * @param size the size
     */
    public void setSizeContent(int size){
        this.size = size;
    }

    /**
     * get The size of real data in payload
     *
     * @return  Size of real data in payload
     */
    public int getSizeContent() {
        return size;
    }

    /** Set the name of the media content of the message.
     *
     * @param name the media name to set
     */
    public void setMediaName(String name) {
        this.mediaName = name;
    }

    /** Set the ID of the message being replied to.
     *
     * @param replyToMessageId the reply-to message ID to set
     */
    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    // ========================= Serialization Methods =========================

    @Override
    public String toString() {
        return "MediaMessage{" +
                "messageId='" + messageId + '\'' +
                ", from=" + from +
                ", to=" + to +
                ", mediaName='" + mediaName + '\'' +
                ", replyToMessageId='" + replyToMessageId + '\'' +
                ", contentSize=" + (content != null ? content.length : 0) +
                '}';
    }
}
