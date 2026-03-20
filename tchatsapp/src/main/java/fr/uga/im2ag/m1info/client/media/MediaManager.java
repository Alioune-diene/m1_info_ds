package fr.uga.im2ag.m1info.client.media;

import fr.uga.im2ag.m1info.client.model.RealMedia;
import fr.uga.im2ag.m1info.client.model.VirtualMedia;
import fr.uga.im2ag.m1info.common.messagefactory.MediaMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static fr.uga.im2ag.m1info.client.Client.MAX_SIZE_CHUNK_FILE;


/**
 * Manages media file reception, chunk reassembly, and storage.
 * Handles the complexity of receiving files in chunks and reassembling them.
 */
public class MediaManager {
    private static final String DOWNLOAD_DIR = ".tchatsapp/downloads";

    private final int clientId;
    private final Path downloadDirectory;
    
    private final Map<String, FileTransfer> activeTransfers;
    private final Map<String, VirtualMedia> completedMedia;
    
    /**
     * Represents a file transfer in progress
     */
    private static class FileTransfer {
        final String mediaId;
        final String fileName;
        final int fromUserId;
        final Instant startTime;
        final ByteArrayOutputStream buffer;
        int chunkCount;
        
        FileTransfer(String mediaId, String fileName, int fromUserId) {
            this.mediaId = mediaId;
            this.fileName = fileName;
            this.fromUserId = fromUserId;
            this.startTime = Instant.now();
            this.buffer = new ByteArrayOutputStream();
            this.chunkCount = 0;
        }
        
        void addChunk(byte[] chunk, int size) {
            buffer.write(chunk, 0, size);
            chunkCount++;
        }
        
        byte[] getCompleteData() {
            return buffer.toByteArray();
        }
        
        long getCurrentSize() {
            return buffer.size();
        }
    }
    
    public MediaManager(int clientId) {
        this.clientId = clientId;
        this.activeTransfers = new ConcurrentHashMap<>();
        this.completedMedia = new ConcurrentHashMap<>();
        
        // Initialize download directory
        String userHome = System.getProperty("user.home");
        this.downloadDirectory = Paths.get(userHome, DOWNLOAD_DIR, String.valueOf(clientId));
        
        try {
            Files.createDirectories(downloadDirectory);
        } catch (IOException e) {
            System.err.println("[MediaManager] Failed to create download directory: " + e.getMessage());
        }
    }
    
    /**
     * Process a received media chunk.
     * Returns VirtualMedia if this is a single-chunk file or the last chunk.
     * Returns null if more chunks are expected.
     * 
     * @param mediaMsg the received MediaMessage
     * @return VirtualMedia if file is complete, null otherwise
     */
    public VirtualMedia processChunk(MediaMessage mediaMsg) {
        String mediaId = generateMediaId(mediaMsg);
        String fileName = extractFileName(mediaMsg.getMediaName());
        int fromUserId = mediaMsg.getFrom();

        byte[] chunkData = mediaMsg.getContent();
        int chunkSize = mediaMsg.getSizeContent();

        FileTransfer transfer = activeTransfers.computeIfAbsent(mediaId, k -> new FileTransfer(mediaId, fileName, fromUserId));
        transfer.addChunk(chunkData, chunkSize);

        if (chunkSize < MAX_SIZE_CHUNK_FILE) {
            try {
                byte[] completeFile = transfer.getCompleteData();

                Path filePath = generateUniqueFilePath(fileName);
                Files.write(filePath, completeFile);

                VirtualMedia virtualMedia = new VirtualMedia(
                        mediaId,
                        fileName,
                        completeFile.length,
                        mediaMsg.getTimestamp(),
                        mediaMsg.getFrom()
                );

                completedMedia.put(mediaId, virtualMedia);
                activeTransfers.remove(mediaId);

                System.out.printf(
                        "Fichier '%s' reçu complètement (%d chunks, %d bytes)%n",
                        fileName, transfer.chunkCount, completeFile.length
                );

                return virtualMedia;
            } catch (IOException e) {
                System.err.println("Erreur lors de l'écriture du fichier: " + e.getMessage());
                e.printStackTrace();
                activeTransfers.remove(mediaId);
                return null;
            }
        } else {
            System.out.printf(
                    "Chunk %d reçu pour '%s' (%d bytes, total: %d bytes)%n",
                    transfer.chunkCount, mediaMsg.getMediaName(),
                    chunkSize, transfer.getCurrentSize()
            );
            return null;
        }
    }
    
    /**
     * Complete a file transfer and save to disk
     */
    private VirtualMedia completeTransfer(FileTransfer transfer) {
        try {
            Path filePath = generateUniqueFilePath(transfer.fileName);
            byte[] completeData = transfer.getCompleteData();
            Files.write(filePath, completeData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            VirtualMedia media = new VirtualMedia(
                transfer.mediaId,
                transfer.fileName,
                completeData.length,
                transfer.startTime,
                transfer.fromUserId
            );
            
            completedMedia.put(transfer.mediaId, media);
            activeTransfers.remove(transfer.mediaId);
            
            System.out.printf("[MediaManager] File transfer complete: %s (%d chunks, %s)%n", transfer.fileName, transfer.chunkCount, media.getFormattedFileSize());
            return media;
        } catch (IOException e) {
            System.err.println("[MediaManager] Failed to save file: " + e.getMessage());
            activeTransfers.remove(transfer.mediaId);
            return null;
        }
    }
    
    /**
     * Load a media file into memory (upgrade VirtualMedia to RealMedia)
     * 
     * @param mediaId the media ID
     * @return RealMedia with content loaded, or null if not found
     */
    public RealMedia loadMedia(String mediaId) {
        VirtualMedia virtual = completedMedia.get(mediaId);
        if (virtual == null) {
            System.err.println("[MediaManager] Media not found: " + mediaId);
            return null;
        }
        
        try {
            Path filePath = findFilePath(virtual.getFileName());
            if (filePath == null) {
                System.err.println("[MediaManager] File not found on disk: " + virtual.getFileName());
                return null;
            }
            
            return new RealMedia(virtual, filePath);
            
        } catch (IOException e) {
            System.err.println("[MediaManager] Failed to load media: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get VirtualMedia for a completed transfer
     */
    public VirtualMedia getVirtualMedia(String mediaId) {
        return completedMedia.get(mediaId);
    }
    
    /**
     * Save a media file to a user-specified location
     * 
     * @param mediaId the media ID
     * @param destinationPath where to save the file
     * @return true if successful
     */
    public boolean saveMediaTo(String mediaId, Path destinationPath) {
        VirtualMedia virtual = completedMedia.get(mediaId);
        if (virtual == null) {
            return false;
        }
        
        try {
            Path sourcePath = findFilePath(virtual.getFileName());
            if (sourcePath == null) {
                return false;
            }
            
            Files.copy(sourcePath, destinationPath, 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            System.out.printf("[MediaManager] File saved to: %s%n", destinationPath);
            return true;
            
        } catch (IOException e) {
            System.err.println("[MediaManager] Failed to save file: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the file path for a media
     */
    public Path getMediaPath(String mediaId) {
        VirtualMedia virtual = completedMedia.get(mediaId);
        if (virtual == null) {
            return null;
        }
        return findFilePath(virtual.getFileName());
    }
    
    /**
     * Get transfer progress for active transfers
     * 
     * @param mediaId the media ID
     * @return progress info or null if not found
     */
    public TransferProgress getTransferProgress(String mediaId) {
        FileTransfer transfer = activeTransfers.get(mediaId);
        if (transfer == null) {
            return null;
        }
        
        return new TransferProgress(
            transfer.mediaId,
            transfer.fileName,
            transfer.getCurrentSize(),
            transfer.chunkCount
        );
    }
    
    /**
     * Extract just the filename from a path
     */
    private String extractFileName(String mediaName) {
        if (mediaName == null) {
            return "unknown_file";
        }
        
        String name = mediaName.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        
        return name.isEmpty() ? "unknown_file" : name;
    }
    
    /**
     * Generate a unique file path to avoid overwriting existing files
     */
    private Path generateUniqueFilePath(String fileName) {
        Path basePath = downloadDirectory.resolve(fileName);
        
        if (!Files.exists(basePath)) {
            return basePath;
        }
        
        String nameWithoutExt = fileName;
        String extension = "";
        
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }
        
        for (int i = 1; i < 1000; i++) {
            Path numbered = downloadDirectory.resolve(nameWithoutExt + "_" + i + extension);
            if (!Files.exists(numbered)) {
                return numbered;
            }
        }
        
        return downloadDirectory.resolve(nameWithoutExt + "_" + System.currentTimeMillis() + extension);
    }
    
    /**
     * Find file path by name in download directory
     */
    private Path findFilePath(String fileName) {
        Path directPath = downloadDirectory.resolve(fileName);
        if (Files.exists(directPath)) {
            return directPath;
        }
        
        String nameWithoutExt = fileName;
        String extension = "";
        
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }
        
        for (int i = 1; i < 1000; i++) {
            Path numbered = downloadDirectory.resolve(nameWithoutExt + "_" + i + extension);
            if (Files.exists(numbered)) {
                return numbered;
            }
        }
        
        return null;
    }

    private String generateMediaId(MediaMessage mediaMsg) {
        int fromUserId = mediaMsg.getFrom();
        int toUserId = mediaMsg.getTo();
        String fileName = mediaMsg.getMediaName();

        return String.format("media_%d_to_%d_%s", fromUserId, toUserId, fileName);
    }
    
    /**
     * Get download directory path
     */
    public Path getDownloadDirectory() {
        return downloadDirectory;
    }
    
    /**
     * Transfer progress information
     */
    public static class TransferProgress {
        public final String mediaId;
        public final String fileName;
        public final long bytesReceived;
        public final int chunksReceived;
        
        public TransferProgress(String mediaId, String fileName, long bytesReceived, int chunksReceived) {
            this.mediaId = mediaId;
            this.fileName = fileName;
            this.bytesReceived = bytesReceived;
            this.chunksReceived = chunksReceived;
        }
    }
}
