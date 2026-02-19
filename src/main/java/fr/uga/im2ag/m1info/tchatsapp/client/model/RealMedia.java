package fr.uga.im2ag.m1info.tchatsapp.client.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Real Proxy for Media - contains full content loaded from disk.
 * Extends VirtualMedia and adds content loading capability.
 */
public class RealMedia extends VirtualMedia {
    private byte[] content;
    private final Path filePath;
    
    /**
     * Create a real media by loading content from file
     * 
     * @param mediaId unique identifier
     * @param fileName original file name
     * @param filePath path to the actual file on disk
     * @param timestamp when the file was sent
     * @param fromUserId who sent the file
     * @throws IOException if file cannot be read
     */
    public RealMedia(String mediaId, String fileName, Path filePath, Instant timestamp, int fromUserId) throws IOException {
        super(mediaId, fileName, Files.size(filePath), timestamp, fromUserId);
        this.filePath = filePath;
        this.content = null;
    }
    
    /**
     * Create a real media from VirtualMedia by loading the file
     * 
     * @param virtual the virtual media to upgrade
     * @param filePath path to the actual file on disk
     * @throws IOException if file cannot be read
     */
    public RealMedia(VirtualMedia virtual, Path filePath) throws IOException {
        super(virtual.getMediaId(), virtual.getFileName(), Files.size(filePath), 
              virtual.getTimestamp(), virtual.getFromUserId());
        this.filePath = filePath;
        this.content = null;
        this.thumbnail = virtual.getThumbnail();
    }
    
    @Override
    public byte[] getContent() {
        if (content == null) {
            loadContent();
        }
        return content != null ? content.clone() : null;
    }
    
    @Override
    public boolean isLoaded() {
        return content != null;
    }
    
    /**
     * Get the file path on disk
     */
    public Path getFilePath() {
        return filePath;
    }
    
    /**
     * Force reload content from disk
     */
    public void reload() throws IOException {
        content = Files.readAllBytes(filePath);
    }
    
    /**
     * Unload content from memory to save space
     */
    public void unload() {
        content = null;
    }
    
    /**
     * Lazy load content from disk
     */
    private void loadContent() {
        try {
            content = Files.readAllBytes(filePath);
        } catch (IOException e) {
            System.err.println("[RealMedia] Failed to load content from " + filePath + ": " + e.getMessage());
            content = null;
        }
    }
    
    @Override
    public String toString() {
        return "RealMedia{" +
                "mediaId='" + mediaId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + getFormattedFileSize() +
                ", mediaType='" + mediaType + '\'' +
                ", filePath=" + filePath +
                ", loaded=" + isLoaded() +
                '}';
    }
}
