package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.config.PredictionProperties;
import com.tony.sportsAnalytics.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PredictionEngineService {

    private final PredictionProperties props;

    private double getEloImpact(int attackerElo, int defenderElo) {
        // Différence de points
        int diff = attackerElo - defenderElo;

        // Chaque 100 points d'écart augmente l'espérance de buts de ~10%
        // Formule sigmoidale pour lisser les écarts extrêmes
        return 1.0 + (diff * 0.001);
    }

    // Méthode utilitaire à appeler si h2hHistory contient des stats détaillées
    private double calculateEfficiencyBonus(Team team, List<MatchAnalysis> history) {
        double totalXg = 0.0;
        double totalGoals = 0.0;
        int count = 0;

        for(MatchAnalysis m : history) {
            // Vérifier si c'est l'équipe Home ou Away et si les stats existent
            if(m.getHomeTeam().equals(team) && m.getHomeMatchStats() != null && m.getHomeMatchStats().getXG() != null) {
                totalXg += m.getHomeMatchStats().getXG();
                totalGoals += m.getHomeScore();
                count++;
            } else if (m.getAwayTeam().equals(team) && m.getAwayMatchStats() != null && m.getAwayMatchStats().getXG() != null) {
                totalXg += m.getAwayMatchStats().getXG();
                totalGoals += m.getAwayScore();
                count++;
            }
        }

        if(count < 3 || totalXg == 0) return 1.0;

        // Ratio Efficacité : Buts Réels / xG
        // Si > 1.0 : Tueurs devant le but. Si < 1.0 : Maladroits.
        double ratio = totalGoals / totalXg;

        // On lisse le ratio pour éviter les extrêmes (max +10% ou -10%)
        return Math.max(0.9, Math.min(1.1, ratio));
    }

    /**
     * Calcule les probabilités du match en utilisant la Loi de Poisson ajustée (Dixon-Coles)
     * et une pondération temporelle (Saison vs Forme).
     */
    public PredictionResult calculateMatchPrediction(MatchAnalysis match, List<MatchAnalysis> h2hHistory,
            List<MatchAnalysis> homeLastMatches, List<MatchAnalysis> awayLastMatches, double leagueAvg) {
        // 1. Calcul des Espérances de Buts (Lambda/Mu) avec Pondération Temporelle (Point 4)
        double homeLambda = calculateExpectedGoals(match.getHomeStats(), match.getAwayStats(), true,
                match.getHomeTeam(), match.getAwayTeam(), leagueAvg);
        double awayLambda = calculateExpectedGoals(match.getAwayStats(), match.getHomeStats(), false,
                match.getAwayTeam(), match.getHomeTeam(), leagueAvg);

        homeLambda = applyContextualFactors(homeLambda, true, match);
        awayLambda = applyContextualFactors(awayLambda, false, match);

        // Si on a de l'historique H2H avec des stats xG, on s'en sert pour affiner
        if (h2hHistory != null && !h2hHistory.isEmpty()) {
            // Calcul du réalisme offensif
            double homeEfficiency = calculateEfficiencyBonus(match.getHomeTeam(), h2hHistory);
            double awayEfficiency = calculateEfficiencyBonus(match.getAwayTeam(), h2hHistory);

            // Application du correctif
            homeLambda *= homeEfficiency;
            awayLambda *= awayEfficiency;

            // Logique H2H psychologique existante (Bonus victoire)
            double h2hFactor = calculateH2HFactor(match.getHomeTeam().getName(), h2hHistory);
            if (h2hFactor > 0) homeLambda *= (1.0 + h2hFactor);
            else awayLambda *= (1.0 + Math.abs(h2hFactor));
        }

        // On analyse la "Dangerosité Réelle" des 5-10 derniers matchs
        double homeTacticalBoost = calculateTacticalBonus(match.getHomeTeam(), homeLastMatches);
        double awayTacticalBoost = calculateTacticalBonus(match.getAwayTeam(), awayLastMatches);

        homeLambda *= homeTacticalBoost;
        awayLambda *= awayTacticalBoost;

        // 3. Matrice de Poisson & Dixon-Coles
        double probHomeWin = 0.0, probDraw = 0.0, probAwayWin = 0.0;
        double probOver2_5 = 0.0, probBTTS = 0.0;

        for (int h = 0; h <= 7; h++) {
            for (int a = 0; a <= 7; a++) {
                double probability = poisson(h, homeLambda) * poisson(a, awayLambda);
                probability = applyDixonColes(probability, h, a, homeLambda, awayLambda);

                if (h > a) probHomeWin += probability;
                else if (h == a) probDraw += probability;
                else probAwayWin += probability;

                if ((h + a) > 2.5) probOver2_5 += probability;
                if (h > 0 && a > 0) probBTTS += probability;
            }
        }

        // Normalisation
        double totalProb = probHomeWin + probDraw + probAwayWin;
        if(totalProb == 0) totalProb = 1.0;

        probHomeWin = (probHomeWin / totalProb) * 100;
        probDraw = (probDraw / totalProb) * 100;
        probAwayWin = (probAwayWin / totalProb) * 100;
        probOver2_5 = (probOver2_5 / totalProb) * 100;
        probBTTS = (probBTTS / totalProb) * 100;

        double dc1N = probHomeWin + probDraw;
        double dcN2 = probDraw + probAwayWin;
        double dc12 = probHomeWin + probAwayWin;

        // Kelly & Value (V9)
        double kellyHome = calculateKelly(probHomeWin / 100.0, match.getOdds1());
        double kellyDraw = calculateKelly(probDraw / 100.0, match.getOddsN());
        double kellyAway = calculateKelly(probAwayWin / 100.0, match.getOdds2());

        String valueSide = null;
        if (kellyHome > 0) valueSide = "HOME";
        else if (kellyAway > 0) valueSide = "AWAY";
        else if (kellyDraw > 0) valueSide = "DRAW";

        // Scores Legacy
        double homePower = calculateLegacyPowerScore(match.getHomeStats(), true);
        double awayPower = calculateLegacyPowerScore(match.getAwayStats(), false);

        return new PredictionResult(
                round(probHomeWin), round(probDraw), round(probAwayWin),
                round(homePower), round(awayPower),
                round(homeLambda), round(awayLambda),
                round(probOver2_5), round(100.0 - probOver2_5),
                round(probBTTS),
                round(dc1N), round(dcN2), round(dc12),
                // Champs V9 Value/Kelly
                kellyHome, kellyDraw, kellyAway, valueSide
        );
    }

    /**
     * V12 : Calcule un multiplicateur basé sur les stats avancées.
     */
    private double calculateTacticalBonus(Team team, List<MatchAnalysis> history) {
        if (history == null || history.isEmpty()) return 1.0;

        double totalXgot = 0.0;
        double totalBigChances = 0.0;
        double totalShots = 0.0;
        double totalShotsOnTarget = 0.0;
        double totalDominance = 0.0;
        int count = 0;

        for (MatchAnalysis m : history) {
            MatchDetailStats stats = null;
            if (m.getHomeTeam().equals(team)) stats = m.getHomeMatchStats();
            else if (m.getAwayTeam().equals(team)) stats = m.getAwayMatchStats();

            if (stats != null) {
                totalXgot += (stats.getXGOT() != null) ? stats.getXGOT() : (stats.getXG() != null ? stats.getXG() : 0);
                totalBigChances += (stats.getBigChances() != null) ? stats.getBigChances() : 0;

                totalShots += (stats.getShots() != null) ? stats.getShots() : 0;
                totalShotsOnTarget += (stats.getShotsOnTarget() != null) ? stats.getShotsOnTarget() : 0;

                double poss = (stats.getPossession() != null) ? stats.getPossession() : 50.0;
                double passAcc = stats.getPassAccuracy();
                totalDominance += (poss * passAcc);

                count++;
            }
        }

        if (count < 3) return 1.0;

        double avgXgot = totalXgot / count;
        double avgBigChances = totalBigChances / count;
        double avgDominance = totalDominance / count;

        // Ratio de précision : Tirs Cadrés / Tirs Totaux
        double shotAccuracy = (totalShots > 0) ? (totalShotsOnTarget / totalShots) : 0.33;

        double bonus = 1.0;

        // 1. Facteur "Tueur" (xGOT élevé)
        if (avgXgot > 1.5) bonus += 0.05;
        if (avgXgot > 2.0) bonus += 0.05;

        // 2. Facteur "Création" (Big Chances)
        if (avgBigChances >= 3.0) bonus += 0.05;

        // 3. Facteur "Contrôle" (Domination)
        if (avgDominance > 45.0) bonus += 0.03;

        // 4. Facteur "Précision" (Nouveau - Utilise shotAccuracy)
        // Si plus de 40% des tirs sont cadrés, c'est une attaque chirurgicale
        if (shotAccuracy > 0.40) bonus += 0.04;

        return bonus;
    }

    /**
     * Point 4 : Calcul de la force offensive/défensive avec pondération (Saison vs Last 5).
     * Point 2 : Avantage Domicile Dynamique.
     */
    private double calculateExpectedGoals(TeamStats attacker, TeamStats defender, boolean isHome,
            Team attackerTeam, Team defenderTeam, double leagueAvg) {
        double attackStrength = calculateWeightedStrength(attacker.getGoalsFor(), attacker.getMatchesPlayed(), attacker.getGoalsForLast5(), 5, leagueAvg);
        double defenseWeakness = calculateWeightedStrength(defender.getGoalsAgainst(), defender.getMatchesPlayed(), defender.getGoalsAgainstLast5(), 5, leagueAvg);

        double expected = attackStrength * defenseWeakness * leagueAvg;

        // Correction ELO
        if (attackerTeam != null && defenderTeam != null) {
            double eloFactor = getEloImpact(attackerTeam.getEloRating(), defenderTeam.getEloRating());
            expected *= eloFactor;
        }

        // Avantage Domicile
        if (isHome) {
            double homeAdvantage = 1.20;
            if (attacker.getVenuePoints() != null && attacker.getVenueMatches() != null && attacker.getVenueMatches() > 0) {
                double ppgHome = (double) attacker.getVenuePoints() / attacker.getVenueMatches();
                double ppgGlobal = (double) attacker.getPoints() / Math.max(1, attacker.getMatchesPlayed());
                if (ppgHome > ppgGlobal) homeAdvantage += 0.1;
            }
            expected *= homeAdvantage;
        } else {
            expected *= 0.85;
        }

        // xG Context
        if (attacker.getXG() != null && attacker.getXG() > 0) {
            double xGFactor = attacker.getXG() / leagueAvg;
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

    private double calculateKelly(double trueProb, Double odds) {
        if (odds == null || odds <= 1.0) return 0.0;
        double b = odds - 1.0;
        double q = 1.0 - trueProb;
        double f = ((b * trueProb) - q) / b;
        return f > 0 ? (f * 0.25) * 100.0 : 0.0;
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
