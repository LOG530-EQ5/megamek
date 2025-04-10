package megamek.server;

public class EloStrategie implements ITournamentScoreStrategie {
    public int kFactor = 32;

    public EloStrategie(int kFactor) {
        this.kFactor = kFactor;
    }

    @Override
    public int[] executeTournamentScoreStrategie(int winnerScore, int loserScore) {
        double expectedWinnerScore = 1.0 / (1.0 + Math.pow(10, (loserScore - winnerScore) / 400.0));
        double expectedLoserScore = 1.0 / (1.0 + Math.pow(10, (winnerScore - loserScore) / 400.0));

        int newWinnerScore = (int) (winnerScore + kFactor * (1 - expectedWinnerScore));
        int newLoserScore = (int) (loserScore + kFactor * (0 - expectedLoserScore));

        return new int[]{newWinnerScore, newLoserScore};
    }
}