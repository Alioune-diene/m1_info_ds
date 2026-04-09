package fr.uga.im2ag.m1info.physical;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads and stores the physical network topology from a JSON configuration file
 * The configuration file describes:
 * How many nodes exist in the network (nodeCount)
 * Which nodes are directly connected to each other (adjacency matrix)
 */
public class NetworkConfig {

    /**
     * The expected JSON format is:
     * <pre>
     * {
     *   "nodeCount": 4,
     *   "adjacency": [
     *     [0, 1, 0, 1],
     *     [1, 0, 1, 0],
     *     [0, 1, 0, 1],
     *     [1, 0, 1, 0]
     *   ]
     * }
     * </pre>
     * <p>
     * nodeCount: total number of nodes in the physical network
     * adjacency: a square matrix where adjacency[i][j] = 1 means node i and node j are directly connected
     */
    private static class JsonConfig {
        @SerializedName("nodeCount")
        int nodeCount;

        @SerializedName("adjacency")
        int[][] adjacency;
    }

    // -------------------- Attributes ---------------------------------------------------------------------------------
    /**
     * Total number of nodes in the physical network
     */
    private final int nodeCount;

    /**
     * Adjacency[i][j] = true means node i and node j are directly connected
     */
    private final boolean[][] adjacency;

    // -------------------- Constructors -------------------------------------------------------------------------------

    /** Private constructor to enforce usage of fromFile() factory method */
    private NetworkConfig(int nodeCount, boolean[][] adjacency) {
        this.nodeCount = nodeCount;
        this.adjacency = adjacency;
    }

    // -------------------- Factory Methods ----------------------------------------------------------------------------

    /**
     * Reads the network configuration from a JSON file and returns a NetworkConfig instance.
     * Validates the input format and values, throwing exceptions for invalid configurations.
     *
     * @param path the path to the JSON configuration file
     * @return a NetworkConfig instance representing the physical network topology
     * @throws IOException if there is an error reading the file
     */
    public static NetworkConfig fromFile(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            Gson gson = new Gson();
            JsonConfig raw = gson.fromJson(reader, JsonConfig.class);
            return parse(raw);
        }
    }

    /**
     * Validates the raw JSON configuration and converts it into a NetworkConfig instance.
     * Checks that nodeCount is positive and that the adjacency matrix is well-formed.
     *
     * @param raw the raw JSON configuration deserialized into a JsonConfig object
     * @return a NetworkConfig instance if the input is valid
     * @throws IllegalArgumentException if the input is invalid (e.g., negative nodeCount, malformed adjacency)
     */
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

    // -------------------- Public Methods -----------------------------------------------------------------------------

    /**
     * Returns an unmodifiable list of node IDs that are directly connected to the given nodeId.
     * Validates that nodeId is within bounds before accessing the adjacency matrix.
     *
     * @param nodeId the ID of the node for which to retrieve neighbors
     * @return an unmodifiable list of neighboring node IDs
     * @throws IllegalArgumentException if nodeId is out of bounds [0, nodeCount-1]
     */
    public List<Integer> getNeighbors(int nodeId) {
        validateNodeId(nodeId);
        List<Integer> neighbors = new ArrayList<>();
        for (int j = 0; j < nodeCount; j++) {
            if (adjacency[nodeId][j]) neighbors.add(j);
        }
        return Collections.unmodifiableList(neighbors);
    }

    /**
     * Returns the total number of nodes in the physical network
     * @return the node count
     */
    public int getNodeCount() {
        return nodeCount;
    }

    /**
     * Validates that the given nodeId is within the valid range [0, nodeCount-1]
     * @param nodeId the node ID to validate
     * @throws IllegalArgumentException if nodeId is out of bounds
     */
    public void validateNodeId(int nodeId) throws IllegalArgumentException {
        if (nodeId < 0 || nodeId >= nodeCount) {
            throw new IllegalArgumentException("nodeId " + nodeId + " is out of bounds [0, " + (nodeCount - 1) + "]");
        }
    }
}
