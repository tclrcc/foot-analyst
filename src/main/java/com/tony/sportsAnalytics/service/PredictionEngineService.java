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

        // 1. Calculer les "Lambda" (Nombre de buts attendus pour chaque équipe)
        // C'est le cœur de la prédiction : combien de buts Home va marquer face à Away ?
        double homeLambda = calculateExpectedGoals(match.getHomeStats(), match.getAwayStats(), true);
        double awayLambda = calculateExpectedGoals(match.getAwayStats(), match.getHomeStats(), false);

        if (h2hHistory != null && !h2hHistory.isEmpty()) {
            double h2hFactor = calculateH2HFactor(match.getHomeTeam(), h2hHistory);
            // Si h2hFactor > 0, l'équipe domicile domine historiquement
            // On ajuste les lambdas (buts attendus)
            if (h2hFactor > 0) homeLambda *= (1.0 + h2hFactor);
            else awayLambda *= (1.0 + Math.abs(h2hFactor));
        }

        // 2. Générer la matrice de probabilités via la Loi de Poisson
        // On simule tous les scores de 0-0 à 5-5
        double[][] scoreMatrix = new double[6][6];
        double probHomeWin = 0.0;
        double probDraw = 0.0;
        double probAwayWin = 0.0;
        double probOver2_5 = 0.0;
        double probBTTS = 0.0;

        for (int h = 0; h <= 5; h++) {
            for (int a = 0; a <= 5; a++) {
                // Proba que Home marque h buts * Proba que Away marque a buts
                double probScore = poisson(h, homeLambda) * poisson(a, awayLambda);
                scoreMatrix[h][a] = probScore;

                // Accumulation pour 1N2
                if (h > a) probHomeWin += probScore;
                else if (h == a) probDraw += probScore;
                else probAwayWin += probScore;

                // Accumulation Over/Under
                if ((h + a) > 2.5) probOver2_5 += probScore;

                // Accumulation BTTS (Les deux > 0)
                if (h > 0 && a > 0) probBTTS += probScore;
            }
        }

        // Normalisation (car on s'arrête à 5-5, il manque ~0.1% de proba infinie)
        double totalProb = probHomeWin + probDraw + probAwayWin;
        probHomeWin = (probHomeWin / totalProb) * 100;
        probDraw = (probDraw / totalProb) * 100;
        probAwayWin = (probAwayWin / totalProb) * 100;
        probOver2_5 = (probOver2_5 / totalProb) * 100;
        probBTTS = (probBTTS / totalProb) * 100;

        // Calcul Double Chance
        double dc1N = probHomeWin + probDraw;
        double dcN2 = probDraw + probAwayWin;
        double dc12 = probHomeWin + probAwayWin;

        // On garde le calcul de PowerScore linéaire pour l'affichage "Force brute"
        double homePower = calculateLegacyPowerScore(match.getHomeStats(), true);
        double awayPower = calculateLegacyPowerScore(match.getAwayStats(), false);

        return new PredictionResult(
                round(probHomeWin), round(probDraw), round(probAwayWin),
                round(homePower), round(awayPower),
                round(homeLambda), round(awayLambda), // Goals prédits
                round(probOver2_5), round(100.0 - probOver2_5), // Over / Under
                round(probBTTS),
                round(dc1N), round(dcN2), round(dc12)
        );
    }

    private double calculateH2HFactor(Team homeTeam, List<MatchAnalysis> h2h) {
        double factor = 0.0;
        int weight = 0;

        // On analyse les 5 derniers matchs max
        for (MatchAnalysis m : h2h) {
            if (m.getHomeScore() == null || m.getAwayScore() == null) continue;

            boolean isHomeCurrent = m.getHomeTeam().equals(homeTeam);
            int goalsCurrent = isHomeCurrent ? m.getHomeScore() : m.getAwayScore();
            int goalsOpponent = isHomeCurrent ? m.getAwayScore() : m.getHomeScore();

            // Victoire = +0.05 de bonus, Défaite = -0.05
            if (goalsCurrent > goalsOpponent) factor += 0.05;
            else if (goalsCurrent < goalsOpponent) factor -= 0.05;

            weight++;
            if (weight >= 5) break;
        }
        return factor; // Ex: +0.10 si 2 victoires de plus que l'adversaire
    }

    /**
     * Calcule le nombre de buts attendus (Lambda)
     * Formule : Force Attaque * Faiblesse Défense Adversaire * Moyenne Ligue
     */
    private double calculateExpectedGoals(TeamStats attacker, TeamStats defender, boolean isHome) {
        double leagueAvg = props.getLeagueAvgGoals(); // ex: 1.35

        // 1. Force Offensive (Attack Strength)
        // Si l'équipe n'a pas de stats GF, on prend la moyenne de ligue (1.0)
        double attackStrength = 1.0;
        if (attacker.getGoalsFor() != null && attacker.getPoints() != null) {
            // On estime le nombre de matchs joués via les points (environ Pts/1.5) ou on prend 10 par défaut si début de saison
            // Pour être précis, il faudrait le nombre exact de matchs joués. Ici on fait une estimation robuste.
            double matchesEstimated = Math.max(5, attacker.getPoints() / 1.4);
            double avgGoalsFor = attacker.getGoalsFor() / matchesEstimated;
            attackStrength = avgGoalsFor / leagueAvg;
        }

        // Bonus xG : Si l'équipe crée beaucoup d'occasions (xG > Buts réels), on booste son potentiel
        if (attacker.getXG() != null) {
            double xGFactor = attacker.getXG() / leagueAvg; // Supposons que l'input xG est "par match"
            attackStrength = (attackStrength * 0.7) + (xGFactor * 0.3); // Mix 70% réalité / 30% xG
        }

        // 2. Faiblesse Défensive (Defense Strength)
        double defenseWeakness = 1.0;
        if (defender.getGoalsAgainst() != null && defender.getPoints() != null) {
            double matchesEstimated = Math.max(5, defender.getPoints() / 1.4);
            double avgGoalsAgainst = defender.getGoalsAgainst() / matchesEstimated;
            defenseWeakness = avgGoalsAgainst / leagueAvg;
        }

        // 3. Calcul de base
        double expectedGoals = attackStrength * defenseWeakness * leagueAvg;

        // 4. Avantage Domicile (environ +20% de buts marqués à domicile)
        if (isHome) {
            expectedGoals *= 1.20;
        } else {
            expectedGoals *= 0.85; // Désavantage extérieur
        }

        // 5. Ajustement Forme (Dynamique)
        if (attacker.getLast5MatchesPoints() != null) {
            double formFactor = attacker.getLast5MatchesPoints() / 7.5; // 7.5 est une forme "moyenne" (1.5 pts * 5)
            // On lisse l'impact de la forme (max +/- 15%)
            expectedGoals *= (0.85 + (0.3 * Math.min(2.0, Math.max(0.0, formFactor))));
        }

        return expectedGoals;
    }

    // Formule mathématique de Poisson
    private double poisson(int k, double lambda) {
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }

    private long factorial(int n) {
        if (n <= 1) return 1;
        return n * factorial(n - 1);
    }

    private double calculateLegacyPowerScore(TeamStats stats, boolean isHome) {
        double score = props.getBaseScore();

        // 1. Avantage Domicile
        if (stats.getVenuePoints() != null && stats.getVenueMatches() != null && stats.getVenueMatches() > 0) {
            double ppgInVenue = (double) stats.getVenuePoints() / stats.getVenueMatches();
            score += ppgInVenue * props.getVenueImportance();
        } else {
            // Fallback si pas de données (ex: début de saison)
            // On donne un petit avantage par défaut au domicile (2.0 pts/m * poids / 2)
            if (isHome) score += 5.0;
        }

        // 2. Points (Réalité comptable)
        if (stats.getPoints() != null) {
            score += stats.getPoints() * props.getPointsImportance();
        }

        // 3. Forme (Dynamique actuelle)
        if (stats.getLast5MatchesPoints() != null) {
            score += stats.getLast5MatchesPoints() * props.getFormImportance();
        }

        // 4. xG (Qualité de la création d'occasions)
        if (stats.getXG() != null) {
            score += stats.getXG() * props.getXgImportance();
        }

        // 5. Classement (Hiérarchie)
        if (stats.getRank() != null) {
            score += (21 - stats.getRank()) * props.getRankImportance();
        }

        // 6. NOUVEAU : Différence de Buts (Force réelle attaque/défense)
        if (stats.getGoalsFor() != null && stats.getGoalsAgainst() != null) {
            int goalDiff = stats.getGoalsFor() - stats.getGoalsAgainst();
            // On ajoute (ou retire) des points basés sur la différence de buts
            score += goalDiff * props.getGoalDiffImportance();
        }

        return score;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
