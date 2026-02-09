package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.config.PredictionProperties;
import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.model.TeamStats;
import com.tony.sportsAnalytics.model.dto.MatchAnalysisRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PredictionEngineService {

    private final PredictionProperties props;

    /**
     * Calcule les probabilités du match en utilisant la Loi de Poisson ajustée (Dixon-Coles)
     * et une pondération temporelle (Saison vs Forme).
     */
    public PredictionResult calculateMatchPrediction(MatchAnalysis match, List<MatchAnalysis> h2hHistory) {
        // 1. Calcul des Espérances de Buts (Lambda/Mu) avec Pondération Temporelle (Point 4)
        double homeLambda = calculateExpectedGoals(match.getHomeStats(), match.getAwayStats(), true);
        double awayLambda = calculateExpectedGoals(match.getAwayStats(), match.getHomeStats(), false);

        homeLambda = applyContextualFactors(homeLambda, true, match);
        awayLambda = applyContextualFactors(awayLambda, false, match);

        // 2. Impact Historique (H2H) - Bonus/Malus psychologique
        if (h2hHistory != null && !h2hHistory.isEmpty()) {
            double h2hFactor = calculateH2HFactor(match.getHomeTeam().getName(), h2hHistory);
            if (h2hFactor > 0) homeLambda *= (1.0 + h2hFactor);
            else awayLambda *= (1.0 + Math.abs(h2hFactor));
        }

        // 3. Matrice de Probabilités (Poisson + Dixon-Coles - Point 2)
        double probHomeWin = 0.0, probDraw = 0.0, probAwayWin = 0.0;
        double probOver2_5 = 0.0, probBTTS = 0.0;

        // On itère jusqu'à 7 buts pour couvrir 99.9% des cas
        for (int h = 0; h <= 7; h++) {
            for (int a = 0; a <= 7; a++) {
                // Probabilité naïve (Poisson standard)
                double probability = poisson(h, homeLambda) * poisson(a, awayLambda);

                // --- AJUSTEMENT DIXON-COLES ---
                // Corrige la sous-estimation des scores faibles (0-0, 1-0, 0-1, 1-1)
                probability = applyDixonColes(probability, h, a, homeLambda, awayLambda);
                // -----------------------------

                // Accumulation 1N2
                if (h > a) probHomeWin += probability;
                else if (h == a) probDraw += probability;
                else probAwayWin += probability;

                // Accumulation Over/Under 2.5
                if ((h + a) > 2.5) probOver2_5 += probability;

                // Accumulation BTTS (Les deux marquent)
                if (h > 0 && a > 0) probBTTS += probability;
            }
        }

        // Normalisation (Indispensable après Dixon-Coles qui peut légèrement modifier la somme totale)
        double totalProb = probHomeWin + probDraw + probAwayWin;
        probHomeWin = (probHomeWin / totalProb) * 100;
        probDraw = (probDraw / totalProb) * 100;
        probAwayWin = (probAwayWin / totalProb) * 100;
        probOver2_5 = (probOver2_5 / totalProb) * 100;
        probBTTS = (probBTTS / totalProb) * 100;

        // Double Chance
        double dc1N = probHomeWin + probDraw;
        double dcN2 = probDraw + probAwayWin;
        double dc12 = probHomeWin + probAwayWin;

        // Calcul Scores Legacy pour affichage
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

    /**
     * Point 4 : Calcul de la force offensive/défensive avec pondération (Saison vs Last 5).
     * Point 2 : Avantage Domicile Dynamique.
     */
    private double calculateExpectedGoals(TeamStats attacker, TeamStats defender, boolean isHome) {
        double leagueAvg = props.getLeagueAvgGoals(); // ex: 1.35

        // A. Force d'Attaque (Pondérée)
        double attackStrength = calculateWeightedStrength(
                attacker.getGoalsFor(), attacker.getMatchesPlayed(),
                attacker.getGoalsForLast5(), 5,
                leagueAvg
        );

        // B. Faiblesse Défensive (Pondérée)
        double defenseWeakness = calculateWeightedStrength(
                defender.getGoalsAgainst(), defender.getMatchesPlayed(),
                defender.getGoalsAgainstLast5(), 5,
                leagueAvg
        );

        // C. Espérance de base
        double expected = attackStrength * defenseWeakness * leagueAvg;

        // D. Avantage Domicile Dynamique (Point 2)
        if (isHome) {
            double homeAdvantage = 1.20; // Base
            // Si l'équipe sur-performe à domicile (Points Home / MJ Home > Points Global / MJ Global)
            if (attacker.getVenuePoints() != null && attacker.getVenueMatches() != null && attacker.getVenueMatches() > 0 &&
                    attacker.getPoints() != null && attacker.getMatchesPlayed() > 0) {

                double ppgHome = (double) attacker.getVenuePoints() / attacker.getVenueMatches();
                double ppgGlobal = (double) attacker.getPoints() / attacker.getMatchesPlayed();

                // Bonus dynamique : max +15% si forteresse imprenable
                if (ppgHome > ppgGlobal) {
                    homeAdvantage += Math.min(0.15, (ppgHome - ppgGlobal) * 0.1);
                }
            }
            expected *= homeAdvantage;
        } else {
            // Désavantage Extérieur (fixe ou dynamique, ici simplifié à 0.85 inversé)
            expected *= 0.85;
        }

        // E. Correction xG (Si disponible et significatif)
        if (attacker.getXG() != null && attacker.getXG() > 0) {
            // Le xG est souvent plus prédictif que les buts réels
            double xGFactor = attacker.getXG() / leagueAvg;
            // On mixe 70% stats calculées + 30% xG pur
            expected = (expected * 0.7) + (xGFactor * leagueAvg * 0.3);
        }

        return expected;
    }

    /**
     * Calcule une force relative (Ratio par rapport à la moyenne) en mixant Saison et Forme.
     */
    private double calculateWeightedStrength(Integer seasonGoals, Integer seasonMatches,
            Integer formGoals, Integer formMatches,
            double leagueAvg) {
        if (seasonMatches == null || seasonMatches == 0) return 1.0;

        // 1. Moyenne Saison
        double avgSeason = (double) seasonGoals / seasonMatches;

        // 2. Moyenne Forme (Last 5)
        // Si pas de données forme, on prend la saison
        double avgForm = avgSeason;
        if (formGoals != null && formMatches != null && formMatches > 0) {
            avgForm = (double) formGoals / formMatches;
        }

        // 3. Moyenne Pondérée
        double weightedAvg = (avgSeason * props.getSeasonWeight()) + (avgForm * props.getFormWeight());

        // 4. Force Relative
        return weightedAvg / leagueAvg;
    }

    /**
     * Applique le correctif de Dixon-Coles pour ajuster les probabilités des scores faibles.
     */
    private double applyDixonColes(double prob, int h, int a, double lambda, double mu) {
        double rho = props.getRho(); // ex: -0.13
        double correction = 1.0;

        if (h == 0 && a == 0) {
            correction = 1.0 - (lambda * mu * rho);
        } else if (h == 0 && a == 1) {
            correction = 1.0 + (lambda * rho);
        } else if (h == 1 && a == 0) {
            correction = 1.0 + (mu * rho);
        } else if (h == 1 && a == 1) {
            correction = 1.0 - rho;
        }

        return prob * correction;
    }

    // --- Helpers (H2H, Poisson, Legacy) ---

    private double poisson(int k, double lambda) {
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }

    private long factorial(int n) {
        if (n <= 1) return 1;
        long fact = 1;
        for (int i = 2; i <= n; i++) fact *= i;
        return fact;
    }

    private double calculateH2HFactor(String homeTeamName, List<MatchAnalysis> h2h) {
        // Simplification pour l'exemple : +5% par victoire dans les 5 derniers H2H
        double factor = 0.0;
        int count = 0;
        for (MatchAnalysis m : h2h) {
            if (m.getHomeScore() == null || m.getAwayScore() == null) continue;
            // Logique basique : Si Home a gagné ce match passé
            boolean isHomeWin = (m.getHomeTeam().getName().equals(homeTeamName) && m.getHomeScore() > m.getAwayScore()) ||
                    (m.getAwayTeam().getName().equals(homeTeamName) && m.getAwayScore() > m.getHomeScore());

            if (isHomeWin) factor += 0.05;
            else factor -= 0.02; // Défaite pèse moins lourd que victoire

            if (++count >= 5) break;
        }
        return factor;
    }

    // Gardé pour rétrocompatibilité UI
    private double calculateLegacyPowerScore(TeamStats stats, boolean isHome) {
        return props.getBaseScore() + (isHome ? 5 : 0) + (stats.getPoints() != null ? stats.getPoints() : 0);
    }

    private double applyContextualFactors(double lambda, boolean isHome, MatchAnalysis match) {
        double factor = 1.0;

        if (isHome) {
            // Si le joueur clé à domicile est absent -> L'attaque chute
            if (match.isHomeKeyPlayerMissing()) factor -= 0.15; // -15%
            // Si l'équipe à domicile est fatiguée -> Performance baisse
            if (match.isHomeTired()) factor -= 0.10; // -10%
        } else {
            // Si le joueur clé extérieur est absent
            if (match.isAwayKeyPlayerMissing()) factor -= 0.15;
            // Choc psychologique (Nouvel entraîneur) -> Boost temporaire
            if (match.isAwayNewCoach()) factor += 0.10; // +10%
        }

        // On applique le facteur au lambda (nombre de buts attendus)
        return lambda * factor;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
