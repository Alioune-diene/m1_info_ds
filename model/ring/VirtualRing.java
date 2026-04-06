package fr.uga.im2ag.m1info.overlay.ring;

import fr.uga.im2ag.m1info.overlay.graph.Graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the virtual ring topology built on top of the physical graph.
 *
 * The simple choice (from the lecture, slide 7): ring order = node IDs in order 1-2-3-4-5-1.
 * A different ring order can be provided (e.g., 1-4-3-5-2-1 from the lecture example).
 *
 * Each node in the ring has:
 *  - a left neighbor  (predecessor in the ring)
 *  - a right neighbor (successor  in the ring)
 *
 * The routing path between a node and its ring neighbors is computed by the Router
 * (BFS on the physical graph).
 */
public class VirtualRing {

    private final List<Integer> ring; // ordered list of node IDs forming the ring

    /** Build the simple ring: nodes sorted by ID (1, 2, 3, ..., n, back to 1). */
    public VirtualRing(Graph graph) {
        ring = new ArrayList<>();
        for (int i = 1; i <= graph.size(); i++) {
            ring.add(i);
        }
    }

    /** Build a ring with a custom node order. */
    public VirtualRing(List<Integer> customOrder) {
        this.ring = new ArrayList<>(customOrder);
    }

    /** Returns the left (predecessor) ring neighbor of nodeId. */
    public int leftNeighbor(int nodeId) {
        int idx = ring.indexOf(nodeId);
        return ring.get((idx - 1 + ring.size()) % ring.size());
    }

    /** Returns the right (successor) ring neighbor of nodeId. */
    public int rightNeighbor(int nodeId) {
        int idx = ring.indexOf(nodeId);
        return ring.get((idx + 1) % ring.size());
    }

    public List<Integer> getOrder() {
        return ring;
    }

    @Override
    public String toString() {
        return ring + " -> (back to " + ring.get(0) + ")";
    }
}
