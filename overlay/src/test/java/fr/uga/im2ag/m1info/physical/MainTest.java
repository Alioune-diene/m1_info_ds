package fr.uga.im2ag.m1info.physical;

import com.ginsberg.junit.exit.assertions.SystemExitAssertion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MainTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private void simulateInput(String input) {
        System.setIn(new ByteArrayInputStream(input.getBytes()));
    }

    @Test
    void main_MissingArgs_ShouldExitWithCode1() {
        SystemExitAssertion.assertThatCallsSystemExit(() -> Main.main(new String[]{})).withExitCode(1);
        assertTrue(errContent.toString().contains("Usage: java -jar"));
    }

    @Test
    void main_ConfigFileNotFound_ShouldExit() {
        try (MockedStatic<NetworkConfig> mockedConfig = mockStatic(NetworkConfig.class)) {
            mockedConfig.when(() -> NetworkConfig.fromFile(any())).thenThrow(new RuntimeException("File error"));

            SystemExitAssertion.assertThatCallsSystemExit(() -> Main.main(new String[]{"0", "config.json"})).withExitCode(1);
        }
    }

    @Test
    void main_PhysicalNodeInitFails_ShouldExit() {
        try (MockedStatic<NetworkConfig> mockedConfig = mockStatic(NetworkConfig.class);
             MockedConstruction<PhysicalNode> ignored = mockConstruction(PhysicalNode.class, (mock, context) -> {
                 throw new RuntimeException("Réseau KO");
             })) {

            mockedConfig.when(() -> NetworkConfig.fromFile(any())).thenReturn(mock(NetworkConfig.class));

            SystemExitAssertion.assertThatCallsSystemExit(() -> Main.main(new String[]{"0", "config.json"})).withExitCode(1);
        }
    }

    @Test
    void main_FullNominalFlow_QuitCommand() {
        simulateInput("quit\n");

        try (MockedStatic<NetworkConfig> mockedConfigStatic = mockStatic(NetworkConfig.class);
             MockedConstruction<PhysicalNode> mockedNode = mockConstruction(PhysicalNode.class);
             MockedConstruction<SpanningTreeManager> ignored1 = mockConstruction(SpanningTreeManager.class);
             MockedConstruction<PhysicalHostService> ignored2 = mockConstruction(PhysicalHostService.class)) {

            NetworkConfig mockConfig = mock(NetworkConfig.class);
            when(mockConfig.getNeighbors(0)).thenReturn(List.of(1, 2));
            mockedConfigStatic.when(() -> NetworkConfig.fromFile(any())).thenReturn(mockConfig);

            Main.main(new String[]{"0", "config.json", "rabbit.host"});

            // Vérifications pour la mutation : on s'assure que les objets ont bien été créés avec les bons paramètres
            assertEquals(1, mockedNode.constructed().size());
            PhysicalNode node = mockedNode.constructed().getFirst();
            verify(node).close(); // Vérifie que "quit" appelle close()
        }
    }

    @Test
    void main_StatusCommand_ShouldPrintInfo() {
        simulateInput("status\nquit\n");

        try (MockedStatic<NetworkConfig> mockedConfigStatic = mockStatic(NetworkConfig.class);
             MockedConstruction<PhysicalNode> ignored1 = mockConstruction(PhysicalNode.class);
             MockedConstruction<SpanningTreeManager> ignored2 = mockConstruction(SpanningTreeManager.class, (mock, context) -> when(mock.getStatusSummary()).thenReturn("ST_OK"));
             MockedConstruction<PhysicalHostService> ignored3 = mockConstruction(PhysicalHostService.class, (mock, context) -> when(mock.getHostedVirtuals()).thenReturn((Set.of(10, 20))))) {

            mockedConfigStatic.when(() -> NetworkConfig.fromFile(any())).thenReturn(mock(NetworkConfig.class));

            Main.main(new String[]{"0", "config.json"});

            assertTrue(outContent.toString().contains("ST_OK"));
            assertTrue(outContent.toString().contains("10"));
            assertTrue(outContent.toString().contains("20"));
        }
    }

    @Test
    void main_InvalidId_ShouldShowError() {
        try {
            Main.main(new String[]{"-1", "config.json"});
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("nodeId -1 is out of bounds"));
        }

        try {
            Main.main(new String[]{"10000", "config.json"});
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("nodeId 10000 is out of bounds"));
        }
    }

    @Test
    void main_SendCommand_Valid_ShouldCallManager() {
        simulateInput("send 2 Hello\nquit\n");

        try (MockedStatic<NetworkConfig> mockedConfigStatic = mockStatic(NetworkConfig.class);
             MockedConstruction<PhysicalNode> ignored1 = mockConstruction(PhysicalNode.class);
             MockedConstruction<SpanningTreeManager> mockedManager = mockConstruction(SpanningTreeManager.class, (mock, context) -> when(mock.getPhase()).thenReturn(SpanningTreeManager.Phase.READY));
             MockedConstruction<PhysicalHostService> ignored2 = mockConstruction(PhysicalHostService.class)) {

            mockedConfigStatic.when(() -> NetworkConfig.fromFile(any())).thenReturn(mock(NetworkConfig.class));

            Main.main(new String[]{"0", "config.json"});

            SpanningTreeManager manager = mockedManager.constructed().getFirst();
            verify(manager).sendData(2, "Hello");
            assertTrue(outContent.toString().contains("-> send to 2 : 'Hello'"));
        }
    }

    @Test
    void main_SendCommand_InvalidDest_ShouldShowError() {
        simulateInput("send abc Hello\nquit\n");

        try (MockedStatic<NetworkConfig> mockedConfigStatic = mockStatic(NetworkConfig.class);
             MockedConstruction<PhysicalNode> ignored1 = mockConstruction(PhysicalNode.class);
             MockedConstruction<SpanningTreeManager> ignored2 = mockConstruction(SpanningTreeManager.class);
             MockedConstruction<PhysicalHostService> ignored3 = mockConstruction(PhysicalHostService.class)) {

            mockedConfigStatic.when(() -> NetworkConfig.fromFile(any())).thenReturn(mock(NetworkConfig.class));

            Main.main(new String[]{"0", "config.json"});

            assertTrue(errContent.toString().contains("dstId must be an integer"));
        }
    }

    @Test
    void main_UnknownCommand_ShouldShowError() {
        simulateInput("invalidCommand\nquit\n");

        try (MockedStatic<NetworkConfig> mockedConfigStatic = mockStatic(NetworkConfig.class);
             MockedConstruction<PhysicalNode> ignored1 = mockConstruction(PhysicalNode.class);
             MockedConstruction<SpanningTreeManager> ignored2 = mockConstruction(SpanningTreeManager.class);
             MockedConstruction<PhysicalHostService> ignore3 = mockConstruction(PhysicalHostService.class)) {

            mockedConfigStatic.when(() -> NetworkConfig.fromFile(any())).thenReturn(mock(NetworkConfig.class));

            Main.main(new String[]{"0", "config.json"});

            assertTrue(errContent.toString().contains("Unknown command"));
        }
    }
}