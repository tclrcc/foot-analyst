package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.config.PredictionProperties;
import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.model.TeamStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PredictionEngineService {

    private final PredictionProperties props;

    public PredictionResult calculateMatchPrediction(MatchAnalysis match, List<MatchAnalysis> h2hHistory) {
        // 1. Calcul des buts attendus (Lambda)
        double homeLambda = calculateExpectedGoals(match.getHomeStats(), match.getAwayStats(), true);
        double awayLambda = calculateExpectedGoals(match.getAwayStats(), match.getHomeStats(), false);

        // 2. Impact Historique (H2H)
        if (h2hHistory != null && !h2hHistory.isEmpty()) {
            double h2hFactor = calculateH2HFactor(match.getHomeTeam(), h2hHistory);
            if (h2hFactor > 0) homeLambda *= (1.0 + h2hFactor);
            else awayLambda *= (1.0 + Math.abs(h2hFactor));
        }

        // 3. Matrice de Poisson (Calcul des probabilités cumulées)
        // On a supprimé 'scoreMatrix' car on calcule les sommes à la volée.
        double probHomeWin = 0.0, probDraw = 0.0, probAwayWin = 0.0;
        double probOver2_5 = 0.0, probBTTS = 0.0;

        for (int h = 0; h <= 5; h++) {
            for (int a = 0; a <= 5; a++) {
                // Probabilité exacte du score (h - a)
                double prob = poisson(h, homeLambda) * poisson(a, awayLambda);

                // Accumulation 1N2
                if (h > a) probHomeWin += prob;
                else if (h == a) probDraw += prob;
                else probAwayWin += prob;

                // Accumulation Over/Under 2.5
                if ((h + a) > 2.5) probOver2_5 += prob;

                // Accumulation BTTS (Les deux équipes marquent)
                if (h > 0 && a > 0) probBTTS += prob;
            }
        }

        // Normalisation (Car la somme s'arrête à 5 buts, il manque une infime probabilité)
        double total = probHomeWin + probDraw + probAwayWin;
        // Protection contre la division par zéro (théorique)
        if (total == 0) total = 1.0;

        probHomeWin = (probHomeWin / total) * 100;
        probDraw = (probDraw / total) * 100;
        probAwayWin = (probAwayWin / total) * 100;
        probOver2_5 = (probOver2_5 / total) * 100;
        probBTTS = (probBTTS / total) * 100;

        double dc1N = probHomeWin + probDraw;
        double dcN2 = probDraw + probAwayWin;
        double dc12 = probHomeWin + probAwayWin;

        // Calcul "Legacy" pour l'affichage des forces
        double homePower = calculateLegacyPowerScore(match.getHomeStats(), true);
        double awayPower = calculateLegacyPowerScore(match.getAwayStats(), false);

        return new PredictionResult(
                round(probHomeWin), round(probDraw), round(probAwayWin),
                round(homePower), round(awayPower),
                round(homeLambda), round(awayLambda),
                round(probOver2_5), round(100.0 - probOver2_5),
                round(probBTTS),
                round(dc1N), round(dcN2), round(dc12)
        );
    }

    private double calculateExpectedGoals(TeamStats attacker, TeamStats defender, boolean isHome) {
        double leagueAvg = props.getLeagueAvgGoals();

        // Attaque
        double attackStrength = 1.0;
        if (attacker.getGoalsFor() != null && attacker.getMatchesPlayed() != null && attacker.getMatchesPlayed() > 0) {
            double avgGF = (double) attacker.getGoalsFor() / attacker.getMatchesPlayed();
            attackStrength = avgGF / leagueAvg;
        } else if (attacker.getGoalsFor() != null && attacker.getPoints() != null) {
            // Fallback si matchesPlayed n'est pas rempli (rétrocompatibilité)
            double estimatedMatches = Math.max(1, attacker.getPoints() / 1.4);
            attackStrength = (attacker.getGoalsFor() / estimatedMatches) / leagueAvg;
        }

        // Défense
        double defenseWeakness = 1.0;
        if (defender.getGoalsAgainst() != null && defender.getMatchesPlayed() != null && defender.getMatchesPlayed() > 0) {
            double avgGA = (double) defender.getGoalsAgainst() / defender.getMatchesPlayed();
            defenseWeakness = avgGA / leagueAvg;
        } else if (defender.getGoalsAgainst() != null && defender.getPoints() != null) {
            // Fallback
            double estimatedMatches = Math.max(1, defender.getPoints() / 1.4);
            defenseWeakness = (defender.getGoalsAgainst() / estimatedMatches) / leagueAvg;
        }

        double expected = attackStrength * defenseWeakness * leagueAvg;

        // Avantage Domicile (Standard)
        if (isHome) expected *= 1.20;
        else expected *= 0.85;

        // Bonus xG (Si dispo)
        if (attacker.getXG() != null) {
            // On suppose que le xG fourni est une moyenne par match
            // Si le xG > Buts attendus calculés, on ajuste légèrement vers le haut.
            if (attacker.getXG() > expected) {
                expected = (expected * 0.8) + (attacker.getXG() * 0.2);
            }
        }

        return expected;
    }

    private double calculateLegacyPowerScore(TeamStats stats, boolean isHome) {
        double score = props.getBaseScore();

        // 1. Performance Contextuelle
        if (stats.getVenuePoints() != null && stats.getVenueMatches() != null && stats.getVenueMatches() > 0) {
            double ppgInVenue = (double) stats.getVenuePoints() / stats.getVenueMatches();
            score += ppgInVenue * props.getVenueImportance();
        } else {
            if (isHome) score += 5.0; // Fallback
        }

        // 2. Points par match (Global)
        if (stats.getPoints() != null && stats.getMatchesPlayed() != null && stats.getMatchesPlayed() > 0) {
            double ppg = (double) stats.getPoints() / stats.getMatchesPlayed();
            score += ppg * props.getPointsImportance() * 20;
        } else if (stats.getPoints() != null) {
            score += stats.getPoints() * props.getPointsImportance(); // Fallback ancien mode
        }

        // 3. Forme
        if (stats.getLast5MatchesPoints() != null) {
            score += stats.getLast5MatchesPoints() * props.getFormImportance();
        }

        // 4. xG
        if (stats.getXG() != null) {
            score += stats.getXG() * props.getXgImportance();
        }

        // 5. Différence de buts
        if (stats.getGoalsFor() != null && stats.getGoalsAgainst() != null) {
            score += (stats.getGoalsFor() - stats.getGoalsAgainst()) * props.getGoalDiffImportance();
        }

        return score;
    }

    private double calculateH2HFactor(Team homeTeam, List<MatchAnalysis> h2h) {
        double factor = 0.0;
        int count = 0;
        for (MatchAnalysis m : h2h) {
            if (m.getHomeScore() == null || m.getAwayScore() == null) continue;
            boolean isHome = m.getHomeTeam().equals(homeTeam);
            int scoreH = isHome ? m.getHomeScore() : m.getAwayScore();
            int scoreA = isHome ? m.getAwayScore() : m.getHomeScore();

            if (scoreH > scoreA) factor += 0.05;
            else if (scoreH < scoreA) factor -= 0.05;

            if (++count >= 5) break;
        }
        return factor;
    }

    private double poisson(int k, double lambda) {
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }

    private long factorial(int n) {
        return (n <= 1) ? 1 : n * factorial(n - 1);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
