package megamek.server;

import java.io.Serializable;

public interface ITournamentScoreStrategie extends Serializable {

    default int[] executeTournamentScoreStrategie(int player1Score, int player2Score) {
        return new int[]{player1Score, player2Score};
    }
}