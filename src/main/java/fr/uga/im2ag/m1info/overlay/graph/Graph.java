package fr.uga.im2ag.m1info.overlay.graph;

import java.util.*;

/**
 * Physical graph topology — adjacency list representation.
 *
 * Dynamic: no need to declare the number of nodes upfront.
 * Nodes are added automatically when an edge involving them is added.
 *
 * Weight-ready: every edge carries a weight (default 1.0).
 * Routing algorithms can ignore it for now and treat all edges equally.
 */
public class Graph {

    // ---------- Edge (weight-ready) ----------

    public static class Edge {
        public final int dst;
        public final double weight;

        public Edge(int dst, double weight) {
            this.dst = dst;
            this.weight = weight;
        }
    }

    // ---------- Adjacency list ----------

    private final Map<Integer, List<Edge>> adj = new HashMap<>();

    /** Add a bidirectional, unweighted edge (weight defaults to 1.0). */
    public void addEdge(int u, int v) {
        addEdge(u, v, 1.0);
    }

    /** Add a bidirectional, weighted edge. */
    public void addEdge(int u, int v, double weight) {
        adj.computeIfAbsent(u, k -> new ArrayList<>()).add(new Edge(v, weight));
        adj.computeIfAbsent(v, k -> new ArrayList<>()).add(new Edge(u, weight));
    }

    public boolean hasEdge(int u, int v) {
        List<Edge> edges = adj.get(u);
        if (edges == null) return false;
        return edges.stream().anyMatch(e -> e.dst == v);
    }

    /** All edges (with weights) leaving node u. */
    public List<Edge> neighbors(int u) {
        return adj.getOrDefault(u, Collections.emptyList());
    }

    /** All node IDs present in the graph. */
    public Set<Integer> nodes() {
        return Collections.unmodifiableSet(adj.keySet());
    }

    /** Number of distinct nodes. */
    public int size() {
        return adj.size();
    }
}
