package megamek.server;

import megamek.common.Player;
import megamek.common.Team;
import megamek.server.ITournamentScoreStrategie;
import java.util.List;

public class EloManager {
    public ITournamentScoreStrategie eloStrategie;

    public EloManager(ITournamentScoreStrategie eloStrategie) {
        this.eloStrategie = eloStrategie;
    }

    public void updateEloTeamPlayers(List<Team> teams, int winningTeam) {
        if (teams.size() == 2) {
            Team team1 = teams.get(0);
            Team team2 = teams.get(1);

            int[] updatedScores;
            if (team1.getId() == winningTeam) {
                updatedScores = eloStrategie.executeTournamentScoreStrategie(team1.getElo(), team2.getElo());
                team1.setElo(updatedScores[0]);
                team2.setElo(updatedScores[1]);
            } else {
                updatedScores = eloStrategie.executeTournamentScoreStrategie(team2.getElo(), team1.getElo());
                team2.setElo(updatedScores[0]);
                team1.setElo(updatedScores[1]);
            }
        }
    }

    public void updateEloPlayers(List<Player> players, int winningPlayer) {
        if (players.size() == 2) {
            Player player1 = players.get(0);
            Player player2 = players.get(1);

            int[] updatedScores;
            if (player1.getId() == winningPlayer) {
                updatedScores = eloStrategie.executeTournamentScoreStrategie(player1.getElo(), player2.getElo());
                player1.setElo(updatedScores[0]);
                player2.setElo(updatedScores[1]);
            } else {
                updatedScores = eloStrategie.executeTournamentScoreStrategie(player2.getElo(), player1.getElo());
                player2.setElo(updatedScores[0]);
                player1.setElo(updatedScores[1]);
            }
        }
    }
}