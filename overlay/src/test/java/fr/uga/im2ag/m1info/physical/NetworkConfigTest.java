package fr.uga.im2ag.m1info.physical;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NetworkConfig}.
 *
 * All tests are purely in-memory (no broker required).
 */
class NetworkConfigTest {

    @TempDir
    Path tmpDir;

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Path writeConfig(String json) throws IOException {
        Path f = tmpDir.resolve("config.json");
        Files.writeString(f, json);
        return f;
    }

    // -------------------------------------------------------------------------
    // Valid configurations
    // -------------------------------------------------------------------------

    @Test
    void fromFile_singleNode_hasNoNeighbors() throws IOException {
        // 1-node graph: adjacency matrix is [[0]]
        Path cfg = writeConfig("{\"nodeCount\":1,\"adjacency\":[[0]]}");
        NetworkConfig nc = NetworkConfig.fromFile(cfg);

        assertEquals(1, nc.getNodeCount());
        assertTrue(nc.getNeighbors(0).isEmpty(), "Isolated node must have no neighbors");
    }

    @Test
    void fromFile_linearChain_neighborsAreCorrect() throws IOException {
        // 0-1-2 chain
        Path cfg = writeConfig(
                "{\"nodeCount\":3,\"adjacency\":[[0,1,0],[1,0,1],[0,1,0]]}");
        NetworkConfig nc = NetworkConfig.fromFile(cfg);

        assertEquals(3, nc.getNodeCount());
        assertEquals(List.of(1), nc.getNeighbors(0));
        assertEquals(List.of(0, 2), nc.getNeighbors(1));
        assertEquals(List.of(1), nc.getNeighbors(2));
    }

    @Test
    void fromFile_fullyConnectedThreeNodes_everyNodeHasTwoNeighbors() throws IOException {
        Path cfg = writeConfig(
                "{\"nodeCount\":3,\"adjacency\":[[0,1,1],[1,0,1],[1,1,0]]}");
        NetworkConfig nc = NetworkConfig.fromFile(cfg);

        for (int i = 0; i < 3; i++) {
            assertEquals(2, nc.getNeighbors(i).size(),
                    "Node " + i + " must have exactly 2 neighbors in a fully-connected 3-node graph");
        }
    }

    @Test
    void getNeighbors_returnsUnmodifiableList() throws IOException {
        Path cfg = writeConfig("{\"nodeCount\":2,\"adjacency\":[[0,1],[1,0]]}");
        NetworkConfig nc = NetworkConfig.fromFile(cfg);

        List<Integer> neighbors = nc.getNeighbors(0);
        assertThrows(UnsupportedOperationException.class, () -> neighbors.add(99),
                "Neighbor list must be unmodifiable");
    }

    // -------------------------------------------------------------------------
    // Invalid configurations — should throw
    // -------------------------------------------------------------------------

    @Test
    void fromFile_nodeCountZero_throwsIllegalArgument() throws IOException {
        Path cfg = writeConfig("{\"nodeCount\":0,\"adjacency\":[]}");
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.fromFile(cfg));
    }

    @Test
    void fromFile_adjacencyRowCountMismatch_throwsIllegalArgument() throws IOException {
        // nodeCount says 3 but only 2 rows
        Path cfg = writeConfig("{\"nodeCount\":3,\"adjacency\":[[0,1],[1,0]]}");
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.fromFile(cfg));
    }

    @Test
    void fromFile_adjacencyColumnCountMismatch_throwsIllegalArgument() throws IOException {
        // nodeCount=2 but row 0 has 3 columns
        Path cfg = writeConfig("{\"nodeCount\":2,\"adjacency\":[[0,1,0],[1,0]]}");
        assertThrows(IllegalArgumentException.class, () -> NetworkConfig.fromFile(cfg));
    }

    // -------------------------------------------------------------------------
    // validateNodeId
    // -------------------------------------------------------------------------

    @Test
    void validateNodeId_negativeId_throwsIllegalArgument() throws IOException {
        Path cfg = writeConfig("{\"nodeCount\":2,\"adjacency\":[[0,1],[1,0]]}");
        NetworkConfig nc = NetworkConfig.fromFile(cfg);

        assertThrows(IllegalArgumentException.class, () -> nc.validateNodeId(-1));

        try {
            nc.validateNodeId(-1);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            System.out.println("Caught expected exception: " + msg);
            assertNotNull(msg, "Exception message should not be null");
            assertTrue(msg.contains("[0, 1]"), "Exception message should indicate the valid range of row indices");
        }
    }

    @Test
    void validateNodeId_idEqualToNodeCount_throwsIllegalArgument() throws IOException {
        Path cfg = writeConfig("{\"nodeCount\":2,\"adjacency\":[[0,1],[1,0]]}");
        NetworkConfig nc = NetworkConfig.fromFile(cfg);

        assertThrows(IllegalArgumentException.class, () -> nc.validateNodeId(2));

        try {
            nc.validateNodeId(2);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertNotNull(msg, "Exception message should not be null");
            assertTrue(msg.contains("[0, 1]"), "Exception message should indicate the valid range of column indices");
        }
    }

    @Test
    void validateNodeId_validId_doesNotThrow() throws IOException {
        Path cfg = writeConfig("{\"nodeCount\":2,\"adjacency\":[[0,1],[1,0]]}");
        NetworkConfig nc = NetworkConfig.fromFile(cfg);

        assertDoesNotThrow(() -> nc.validateNodeId(0));
        assertDoesNotThrow(() -> nc.validateNodeId(1));
    }

    // -------------------------------------------------------------------------
    // getNeighbors out-of-bounds
    // -------------------------------------------------------------------------

    @Test
    void getNeighbors_invalidNodeId_throwsIllegalArgument() throws IOException {
        Path cfg = writeConfig("{\"nodeCount\":2,\"adjacency\":[[0,1],[1,0]]}");
        NetworkConfig nc = NetworkConfig.fromFile(cfg);

        assertThrows(IllegalArgumentException.class, () -> nc.getNeighbors(5));
    }
}
