package fr.uga.im2ag.m1info.common;

/** Interface for generating unique message IDs. */
public interface MessageIdGenerator {
    String generateId(int userId, long timestamp);
}
