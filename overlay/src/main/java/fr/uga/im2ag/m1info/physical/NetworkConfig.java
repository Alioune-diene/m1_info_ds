package fr.uga.im2ag.m1info.physical;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
//Reads and stores the physical network topology from a JSON configuration file
//The configuration file describes:
//How many nodes exist in the network (nodeCount)
// Which nodes are directly connected to each other (adjacency matrix)

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class NetworkConfig {
    private static class JsonConfig {
        @SerializedName("nodeCount")
        int nodeCount;

        @SerializedName("adjacency")
        int[][] adjacency;
    }
      // Total number of nodes in the physical network
    private final int nodeCount;
     // adjacency[i][j] = true means node i and node j are directly connected
    private final boolean[][] adjacency;

    private NetworkConfig(int nodeCount, boolean[][] adjacency) {
        this.nodeCount = nodeCount;
        this.adjacency = adjacency;
    }

    public static NetworkConfig fromFile(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            Gson gson = new Gson();
            JsonConfig raw = gson.fromJson(reader, JsonConfig.class);
            return parse(raw);
        }
    }

    private static NetworkConfig parse(JsonConfig raw) {
        if (raw.nodeCount <= 0) { throw new IllegalArgumentException("nodeCount must be > 0"); }
        if (raw.adjacency == null || raw.adjacency.length != raw.nodeCount) { throw new IllegalArgumentException("Adjacency matrix must have " + raw.nodeCount + " rows"); }

        boolean[][] adj = new boolean[raw.nodeCount][raw.nodeCount];
        for (int i = 0; i < raw.nodeCount; i++) {
            if (raw.adjacency[i].length != raw.nodeCount) {
                throw new IllegalArgumentException("Adjacency matrix must have " + raw.nodeCount + " rows");
            }
            for (int j = 0; j < raw.nodeCount; j++) {
                adj[i][j] = raw.adjacency[i][j] != 0;
            }
        }
        return new NetworkConfig(raw.nodeCount, adj);
    }
    // Returns the list of nodes directly connected (neighbors) to a given node
    //Used by each node at startup to know who it can talk to directly.
    public List<Integer> getNeighbors(int nodeId) {
        validateNodeId(nodeId);
        List<Integer> neighbors = new ArrayList<>();
        for (int j = 0; j < nodeCount; j++) {
            if (adjacency[nodeId][j]) neighbors.add(j);
        }
        return Collections.unmodifiableList(neighbors);
    }

    public int getNodeCount() { return nodeCount; }

    public void validateNodeId(int nodeId) {
        if (nodeId < 0 || nodeId >= nodeCount) {
            throw new IllegalArgumentException("nodeId " + nodeId + " is out of bounds [0, " + (nodeCount - 1) + "]");
        }
    }
}
