package megamek.server;

public interface ITournamentScoreStrategie {
    private int kFactor;

    public ITournamentScoreStrategie(int kFactor) {
        this.kFactor = kFactor;
    }

    public int[] executeTournamentScoreStrategie(int player1Score, int player2Score) {}
}