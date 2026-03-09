package fr.uga.im2ag.m1info.overlay.ring;

import fr.uga.im2ag.m1info.overlay.graph.Graph;

import java.util.*;

/**
 * Computes shortest paths on the physical graph using BFS.
 * Used to route messages between virtual ring neighbors that may not be
 * directly connected in the physical topology.
 */
public class Router {

    private final Graph graph;

    public Router(Graph graph) {
        this.graph = graph;
    }

    /**
     * Returns the shortest physical path from src to dst as an ordered list of node IDs.
     * e.g. BFS on the lecture graph: path(1, 4) = [1, 3, 2, 4]
     *
     * @return list including src and dst, or empty list if no path exists
     */
    public List<Integer> shortestPath(int src, int dst) {
        if (src == dst) return List.of(src);

        Map<Integer, Integer> parent = new HashMap<>();
        Queue<Integer> queue = new LinkedList<>();
        parent.put(src, -1);
        queue.add(src);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int neighbor : graph.neighbors(current)) {
                if (!parent.containsKey(neighbor)) {
                    parent.put(neighbor, current);
                    if (neighbor == dst) {
                        return buildPath(parent, src, dst);
                    }
                    queue.add(neighbor);
                }
            }
        }
        return Collections.emptyList(); // no path found
    }

    private List<Integer> buildPath(Map<Integer, Integer> parent, int src, int dst) {
        LinkedList<Integer> path = new LinkedList<>();
        int current = dst;
        while (current != src) {
            path.addFirst(current);
            current = parent.get(current);
        }
        path.addFirst(src);
        return path;
    }
}
