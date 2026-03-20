package fr.uga.im2ag.m1info.client.model;

/**
 * Media Proxy Interface
 */
public interface Media {
    String getMediaId();
    String getMediaType();
    byte[] getThumbnail();
    byte[] getContent();
    boolean isLoaded();
}
