package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.Team;
import org.springframework.stereotype.Service;

@Service
public class EloService {

    // Facteur K : La volatilité.
    // K=30 est agressif (bien pour le foot où la forme change vite).
    // K=20 est standard.
    private static final int K_FACTOR = 30;

    /**
     * Met à jour les scores ELO des deux équipes après un match réel.
     * @param homeTeam L'équipe domicile
     * @param awayTeam L'équipe extérieur
     * @param homeGoals Buts domicile
     * @param awayGoals Buts extérieur
     */
    public void updateRatings(Team homeTeam, Team awayTeam, int homeGoals, int awayGoals) {
        double actualScoreHome = 0.5;
        if (homeGoals > awayGoals) actualScoreHome = 1.0;
        else if (homeGoals < awayGoals) actualScoreHome = 0.0;

        // Calcul de l'espérance de victoire basée sur l'écart ELO actuel
        double expectedScoreHome = 1.0 / (1.0 + Math.pow(10.0, (awayTeam.getEloRating() - homeTeam.getEloRating()) / 400.0));

        // Formule ELO Standard : Nouveau = Ancien + K * (Réel - Attendu)
        int delta = (int) (K_FACTOR * (actualScoreHome - expectedScoreHome));

        // Ajustement sur la marge de victoire (Bonus si massacre, ex: 5-0)
        int goalDiff = Math.abs(homeGoals - awayGoals);
        if (goalDiff > 1) {
            // Multiplicateur logarithmique pour ne pas exploser les scores sur un 10-0
            delta = (int) (delta * Math.log(goalDiff + 1));
        }

        // Application
        homeTeam.setEloRating(homeTeam.getEloRating() + delta);
        awayTeam.setEloRating(awayTeam.getEloRating() - delta);
    }
}
