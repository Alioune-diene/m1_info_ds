package fr.uga.im2ag.m1info.overlay.graph;

import java.io.*;
import java.util.*;

/**
 * Loads a Graph from a compact edge-list file.
 *
 * Format: a flat sequence of integer pairs (u v) representing bidirectional edges.
 * The sequence ends with -1. Pairs can be spread freely across multiple lines.
 *
 * Example (lecture 5-node graph):
 *   1 3  2 3  2 4  2 5  -1
 *
 * Or split across lines:
 *   1 3 2 3
 *   2 4 2 5 -1
 *
 * Properties:
 *  - No need to declare n upfront — graph size is dynamic.
 *  - Sparse-friendly: only edges are stored, no n×n matrix in the file.
 *  - Only one direction per edge needs to be listed (addEdge is symmetric).
 */
public class GraphLoader {

    public static Graph load(String filePath) throws IOException {
        Graph graph = new Graph();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            List<Integer> tokens = readTokens(br);
            int i = 0;
            while (i < tokens.size()) {
                int token = tokens.get(i);
                if (token == -1) break;
                int u = token;
                int v = tokens.get(i + 1); // pair: (u, v)
                graph.addEdge(u, v);
                i += 2;
            }
        }

        return graph;
    }

    /** Tokenizes the whole file into a flat list of integers. */
    private static List<Integer> readTokens(BufferedReader br) throws IOException {
        List<Integer> tokens = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            for (String part : line.trim().split("\\s+")) {
                if (!part.isEmpty()) tokens.add(Integer.parseInt(part));
            }
        }
        return tokens;
    }
}
