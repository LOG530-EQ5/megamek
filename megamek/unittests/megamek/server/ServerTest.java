package megamek.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import megamek.client.ui.swing.util.PlayerColour;
import megamek.common.Game;
import megamek.common.Player;
import megamek.common.enums.GamePhase;
import megamek.common.force.Forces;
import megamek.common.options.GameOptions;
import megamek.server.totalwarfare.TWGameManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class ServerTest {
    private Server server;

    @BeforeEach
    void setUp() throws IOException {
        TWGameManager gameManager = new TWGameManager();

        server = new Server("1234",2,gameManager); // Adjusted to match constructor requiring a port number
    }

    @Test
    void testCreatePlayer() throws RemoteException {
        int connId = 4;
        String playerName = "TestPlayer";
        boolean isBot = false;
        Player newPlayer = server.createPlayer(connId, playerName, isBot);

        assertNotNull(newPlayer);
        assertEquals(playerName, newPlayer.getName());
        assertEquals(connId, newPlayer.getId());
        assertFalse(newPlayer.isBot());

        assertNotNull(newPlayer.getColour());

        assertTrue(newPlayer.getTeam() >= 0 && newPlayer.getTeam() <= 5);
    }

    @Test
    void testGetPlayerColour() throws RemoteException {
        Player existingPlayer = new Player(1, "Existing");
        existingPlayer.setColour(PlayerColour.RED);

        Player testPlayer = new Player(2, "New");
        testPlayer.setColour(PlayerColour.RED);

        server.getGame().addPlayer(existingPlayer.getId(), existingPlayer);

        PlayerColour assigned = server.getPlayerColour(testPlayer);

        assertEquals(PlayerColour.GREEN, assigned);
    }
}
