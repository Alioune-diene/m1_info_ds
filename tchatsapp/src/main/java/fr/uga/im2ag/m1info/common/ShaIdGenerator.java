package fr.uga.im2ag.m1info.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

/** Utility class for generating unique message IDs,
 * using SHA-256, user ID, timestamp, and a counter.
 * Security is not a goal here, just uniqueness.
 * <p>
 * Message ID can be useful to have responses to specific messages,
 * or reactions, for example.
 */
public class ShaIdGenerator implements MessageIdGenerator {
    private final AtomicLong counter;

    public ShaIdGenerator() {
        this.counter = new AtomicLong(0);
    }

    public ShaIdGenerator(long startCounter) {
        this.counter = new AtomicLong(startCounter);
    }

    public String generateId(int userId, long timestamp) {
        long count = counter.getAndIncrement();
        return generateHash(userId, timestamp, count);
    }

    private String generateHash(int userId, long timestamp, long counter) {
        try {
            String input = userId + "-" + timestamp + "-" + counter;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating message ID", e);
        }
    }
}
