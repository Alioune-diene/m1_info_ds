package fr.uga.im2ag.m1info.tchatsapp.client.model;

import java.time.Instant;

/**
 * Virtual Proxy for Media - contains only metadata without loading full content.
 * Implements lazy loading pattern for memory efficiency.
 */
public class VirtualMedia implements Media {
    protected final String mediaId;
    protected final String fileName;
    protected final long fileSize;
    protected final String mediaType;
    protected final Instant timestamp;
    protected final int fromUserId;
    protected byte[] thumbnail;
    
    /**
     * Create a virtual media with metadata only
     * 
     * @param mediaId unique identifier (messageId of first chunk)
     * @param fileName original file name
     * @param fileSize total size in bytes
     * @param timestamp when the file was sent
     * @param fromUserId who sent the file
     */
    public VirtualMedia(String mediaId, String fileName, long fileSize, Instant timestamp, int fromUserId) {
        this.mediaId = mediaId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mediaType = extractMediaType(fileName);
        this.timestamp = timestamp;
        this.fromUserId = fromUserId;
        this.thumbnail = null;
    }
    
    @Override
    public String getMediaId() {
        return mediaId;
    }
    
    @Override
    public String getMediaType() {
        return mediaType;
    }
    
    @Override
    public byte[] getThumbnail() {
        return thumbnail != null ? thumbnail.clone() : null;
    }
    
    @Override
    public byte[] getContent() {
        throw new UnsupportedOperationException(
            "Content not loaded. Use MediaManager.loadMedia() to get full content."
        );
    }
    
    @Override
    public boolean isLoaded() {
        return false;
    }
    
    /**
     * Get the file name
     */
    public String getFileName() {
        return fileName;
    }
    
    /**
     * Get the file size in bytes
     */
    public long getFileSize() {
        return fileSize;
    }
    
    /**
     * Get timestamp when file was sent
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get user ID who sent the file
     */
    public int getFromUserId() {
        return fromUserId;
    }
    
    /**
     * Set thumbnail data (for images)
     */
    public void setThumbnail(byte[] thumbnail) {
        this.thumbnail = thumbnail != null ? thumbnail.clone() : null;
    }
    
    /**
     * Get human-readable file size
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Extract media type from file extension
     */
    private static String extractMediaType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "application/octet-stream";
        }
        
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
        return switch (extension) {
            // Images
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            
            // Documents
            case "pdf" -> "application/pdf";
            case "doc", "docx" -> "application/msword";
            case "xls", "xlsx" -> "application/vnd.ms-excel";
            case "ppt", "pptx" -> "application/vnd.ms-powerpoint";
            case "txt" -> "text/plain";
            
            // Archives
            case "zip" -> "application/zip";
            case "rar" -> "application/x-rar-compressed";
            case "7z" -> "application/x-7z-compressed";
            case "tar" -> "application/x-tar";
            case "gz" -> "application/gzip";
            
            // Audio
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            
            // Video
            case "mp4" -> "video/mp4";
            case "avi" -> "video/x-msvideo";
            case "mkv" -> "video/x-matroska";
            
            default -> "application/octet-stream";
        };
    }
    
    /**
     * Check if media is an image
     */
    public boolean isImage() {
        return mediaType.startsWith("image/");
    }
    
    /**
     * Get icon representation based on media type
     */
    public String getIcon() {
        if (mediaType.startsWith("image/")) return "[Image]";
        if (mediaType.startsWith("video/")) return "[Video]";
        if (mediaType.startsWith("audio/")) return "[Audio]";
        if (mediaType.equals("application/pdf")) return "[PDF Document]";
        if (mediaType.contains("word")) return "[Word Document]";
        if (mediaType.contains("excel") || mediaType.contains("spreadsheet")) return "[Excel Spreadsheet]";
        if (mediaType.contains("powerpoint") || mediaType.contains("presentation")) return "[PowerPoint Presentation]";
        if (mediaType.contains("zip") || mediaType.contains("rar") || mediaType.contains("7z")
            || mediaType.contains("tar") || mediaType.contains("gzip")) return "[Compressed Archive]";
        return "[File]";
    }
    
    @Override
    public String toString() {
        return "VirtualMedia{" +
                "mediaId='" + mediaId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + getFormattedFileSize() +
                ", mediaType='" + mediaType + '\'' +
                '}';
    }
}
