package fr.uga.im2ag.m1info.server;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An IdGenerator that generates sequential integer IDs starting from 0 or from a specified start value.
 */
public class SequentialIdGenerator implements IdGenerator {
    private final AtomicInteger nextId;

    /**
     * Creates a SequantialIdGenerator that starts generating IDs from 1.
     */
    public SequentialIdGenerator() {
        this.nextId = new AtomicInteger(1);
    }

    /**
     * Creates a SequantialIdGenerator that starts generating IDs from the specified start value.
     *
     * @param start the starting value for ID generation
     */
    public SequentialIdGenerator(int start) {
        this.nextId = new AtomicInteger(start);
    }

    /**
     * Generates and returns the next sequential ID.
     *
     * @return the next sequential ID
     */
    @Override
    public int generateId() {
        return nextId.getAndIncrement();
    }
}
