package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;

/**
 * Event fired when a file chunk is received during transfer
 */
public class FileTransferProgressEvent extends Event {
    private final String conversationId;
    private final String mediaId;
    private final String fileName;
    private final long bytesReceived;
    private final int chunksReceived;
    private final boolean isComplete;
    
    public FileTransferProgressEvent(Object source, String conversationId, String mediaId, 
                                    String fileName, long bytesReceived, int chunksReceived, 
                                    boolean isComplete) {
        super(source);
        this.conversationId = conversationId;
        this.mediaId = mediaId;
        this.fileName = fileName;
        this.bytesReceived = bytesReceived;
        this.chunksReceived = chunksReceived;
        this.isComplete = isComplete;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public String getMediaId() {
        return mediaId;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public long getBytesReceived() {
        return bytesReceived;
    }
    
    public int getChunksReceived() {
        return chunksReceived;
    }
    
    public boolean isComplete() {
        return isComplete;
    }
    
    public String getFormattedProgress() {
        double mb = bytesReceived / (1024.0 * 1024.0);
        return String.format("%.2f MB", mb);
    }
    
    @Override
    public Class<? extends Event> getEventType() {
        return FileTransferProgressEvent.class;
    }
}
