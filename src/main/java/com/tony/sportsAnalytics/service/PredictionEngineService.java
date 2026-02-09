package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.config.PredictionProperties;
import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.MatchDetailStats;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.model.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PredictionEngineService {

    private final PredictionProperties props;

    /**
     * Algorithme V14 : Performance-Based Predictive Model (xG + Control + Efficiency)
     */
    public PredictionResult calculateMatchPrediction(MatchAnalysis match,
            List<MatchAnalysis> h2hHistory,
            List<MatchAnalysis> homeLastMatches,
            List<MatchAnalysis> awayLastMatches,
            double leagueAvgGoals) {

        // 1. ANALYSE DE FORME AVANCÉE (xG & xGA)
        // On calcule la force offensive et défensive basée sur les stats détaillées des 10 derniers matchs
        TeamPerformance homePerf = analyzeTeamPerformance(match.getHomeTeam(), homeLastMatches, leagueAvgGoals, true);
        TeamPerformance awayPerf = analyzeTeamPerformance(match.getAwayTeam(), awayLastMatches, leagueAvgGoals, false);

        // 2. CALCUL DES LAMBDAS (Espérance de buts)
        // Formule : (Attaque Dom * Défense Ext * Moyenne Ligue) corrigé par le réalisme
        double rawHomeXg = homePerf.attackStrength * awayPerf.defenseWeakness * leagueAvgGoals;
        double rawAwayXg = awayPerf.attackStrength * homePerf.defenseWeakness * leagueAvgGoals;

        // 3. FACTEUR DE RÉALISME (Conversion Rate)
        // Si une équipe surperforme ses xG régulièrement (Real Madrid), on booste son espérance
        double homeLambda = rawHomeXg * homePerf.finishingEfficiency;
        double awayLambda = rawAwayXg * awayPerf.finishingEfficiency;

        // 4. FACTEURS CONTEXTUELS
        // Avantage Domicile (renforcé par le public/voyage)
        homeLambda *= 1.20;
        awayLambda *= 0.85;

        // Ajustement ELO (Écart de niveau pur)
        double eloFactor = getEloImpact(match.getHomeTeam().getEloRating(), match.getAwayTeam().getEloRating());
        homeLambda *= eloFactor; // Si Dom est plus fort, lambda augmente. Sinon il baisse (géré par getEloImpact modifié)

        // Ajustement Tactique (Possession/Contrôle)
        // Si une équipe prive l'autre de ballon, l'autre marquera moins (réduction du lambda adverse)
        if (homePerf.avgPossession > 60.0 && awayPerf.avgPossession < 45.0) {
            awayLambda *= 0.90; // Domination stérile de Dom, mais prive Ext de ballons
        } else if (awayPerf.avgPossession > 60.0) {
            homeLambda *= 0.90;
        }

        // Ajustement Blessures / Fatigue (Saisie manuelle)
        homeLambda = applyContextualFactors(homeLambda, true, match);
        awayLambda = applyContextualFactors(awayLambda, false, match);

        // Ajustement H2H (Bête noire)
        if (h2hHistory != null && !h2hHistory.isEmpty()) {
            double h2hFactor = calculateH2HFactor(match.getHomeTeam().getName(), h2hHistory);
            homeLambda *= (1.0 + h2hFactor);
            // L'inverse est appliqué à l'extérieur implicitement par le ratio de force
        }

        // 5. SIMULATION (Loi de Poisson Bivariée / Dixon-Coles)
        return simulateMatch(homeLambda, awayLambda, match.getOdds1(), match.getOddsN(), match.getOdds2());
    }

    // --- STRUCTURES INTERNES ---

    private record TeamPerformance(
            double attackStrength,      // Capacité à créer des xG
            double defenseWeakness,     // Tendance à concéder des xG
            double finishingEfficiency, // Ratio Buts/xG (Tueur vs Maladroit)
            double defensiveLuck,       // Ratio ButsEncaissés/xGA (Bon gardien vs Passoire)
            double avgPossession
    ) {}

    // --- ANALYSEUR STATISTIQUE ---

    private TeamPerformance analyzeTeamPerformance(Team team, List<MatchAnalysis> history, double leagueAvg, boolean isHomeAnalysis) {
        if (history == null || history.isEmpty()) {
            return new TeamPerformance(1.0, 1.0, 1.0, 1.0, 50.0);
        }

        double totalXgFor = 0.0;
        double totalXgAgainst = 0.0;
        double totalGoalsFor = 0.0;
        double totalGoalsAgainst = 0.0;
        double totalPossession = 0.0;

        // Variables Tirs
        double totalShots = 0.0;
        double totalShotsOnTarget = 0.0;

        int count = 0;
        double currentWeight = 1.0;
        double weightSum = 0.0;

        for (MatchAnalysis m : history) {
            boolean wasHome = m.getHomeTeam().equals(team);

            MatchDetailStats myStats = wasHome ? m.getHomeMatchStats() : m.getAwayMatchStats();
            MatchDetailStats oppStats = wasHome ? m.getAwayMatchStats() : m.getHomeMatchStats();

            Integer homeScoreObj = m.getHomeScore();
            Integer awayScoreObj = m.getAwayScore();

            int scoreHome = (homeScoreObj != null) ? homeScoreObj : 0;
            int scoreAway = (awayScoreObj != null) ? awayScoreObj : 0;

            int myGoals = wasHome ? scoreHome : scoreAway;
            int oppGoals = wasHome ? scoreAway : scoreHome;

            double xgFor = (myStats != null && myStats.getXG() != null) ? myStats.getXG() : (myGoals * 0.8);
            double xgAgainst = (oppStats != null && oppStats.getXG() != null) ? oppStats.getXG() : (oppGoals * 0.8);
            double poss = (myStats != null && myStats.getPossession() != null) ? myStats.getPossession() : 50.0;

            // Pondération Contexte (Dom/Ext)
            double contextFactor = (wasHome == isHomeAnalysis) ? 1.10 : 0.90;
            double finalWeight = currentWeight * contextFactor;

            // Cumul des Stats
            totalXgFor += xgFor * finalWeight;
            totalXgAgainst += xgAgainst * finalWeight;
            totalGoalsFor += myGoals * finalWeight;
            totalGoalsAgainst += oppGoals * finalWeight;
            totalPossession += poss * finalWeight;

            // Cumul des Tirs (Avec sécurité null)
            if (myStats != null) {
                if (myStats.getShots() != null) {
                    totalShots += myStats.getShots() * finalWeight;
                }
                if (myStats.getShotsOnTarget() != null) {
                    totalShotsOnTarget += myStats.getShotsOnTarget() * finalWeight;
                }
            }

            weightSum += finalWeight;
            count++;

            currentWeight *= 0.9;
            if (count >= 10) break;
        }

        if (weightSum == 0) return new TeamPerformance(1.0, 1.0, 1.0, 1.0, 50.0);

        // Moyennes
        double avgXgFor = totalXgFor / weightSum;
        double avgXgAgainst = totalXgAgainst / weightSum;
        double avgGoalsFor = totalGoalsFor / weightSum;
        double avgGoalsAgainst = totalGoalsAgainst / weightSum;
        double avgPoss = totalPossession / weightSum;

        // --- CORRECTION : UTILISATION DES VARIABLES DE TIRS ---
        // On calcule la précision moyenne (Cadrés / Totaux)
        double shotAccuracy = (totalShots > 0) ? (totalShotsOnTarget / totalShots) : 0.33; // 33% par défaut

        // Calcul Ratios
        double attackStrength = (leagueAvg > 0) ? (avgXgFor / leagueAvg) : 1.0;
        double defenseWeakness = (leagueAvg > 0) ? (avgXgAgainst / leagueAvg) : 1.0;

        // Efficacité (Finition) de base : Buts / xG
        double finishing = (avgXgFor > 0.1) ? (avgGoalsFor / avgXgFor) : 1.0;

        // BONUS/MALUS PRÉCISION
        // Si l'équipe cadre plus de 40% de ses tirs, elle est chirurgicale -> Bonus +5% finition
        if (shotAccuracy > 0.40) finishing *= 1.05;
            // Si l'équipe cadre moins de 28% de ses tirs, elle est maladroite -> Malus -5% finition
        else if (shotAccuracy < 0.28) finishing *= 0.95;

        // Bornage final pour éviter les extrêmes
        finishing = Math.max(0.75, Math.min(1.30, finishing));

        double defLuck = (avgXgAgainst > 0.1) ? (avgGoalsAgainst / avgXgAgainst) : 1.0;
        defLuck = Math.max(0.80, Math.min(1.40, defLuck));

        return new TeamPerformance(attackStrength, defenseWeakness, finishing, defLuck, avgPoss);
    }

    // --- SIMULATION & RÉSULTATS ---

    private PredictionResult simulateMatch(double lambdaHome, double lambdaAway, Double odds1, Double oddsN, Double odds2) {
        double probHomeWin = 0.0, probDraw = 0.0, probAwayWin = 0.0;
        double probOver2_5 = 0.0, probBTTS = 0.0;

        // Matrice de Poisson (jusqu'à 9 buts pour précision)
        for (int h = 0; h <= 9; h++) {
            for (int a = 0; a <= 9; a++) {
                double probability = poisson(h, lambdaHome) * poisson(a, lambdaAway);

                // Correction Dixon-Coles (Dépendance des scores faibles)
                probability = applyDixonColes(probability, h, a, lambdaHome, lambdaAway);

                if (h > a) probHomeWin += probability;
                else if (h == a) probDraw += probability;
                else probAwayWin += probability;

                if ((h + a) > 2.5) probOver2_5 += probability;
                if (h > 0 && a > 0) probBTTS += probability;
            }
        }

        // Normalisation (Total ~ 1.0)
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

        // Kelly Criterion
        double kellyHome = calculateKelly(probHomeWin / 100.0, odds1);
        double kellyDraw = calculateKelly(probDraw / 100.0, oddsN);
        double kellyAway = calculateKelly(probAwayWin / 100.0, odds2);

        String valueSide = null;
        if (kellyHome > 0) valueSide = "HOME";
        else if (kellyAway > 0) valueSide = "AWAY";
        else if (kellyDraw > 0) valueSide = "DRAW";

        // Scores de Puissance (Esthétique)
        double homePower = Math.min(99, lambdaHome * 25);
        double awayPower = Math.min(99, lambdaAway * 25);

        return new PredictionResult(
                round(probHomeWin), round(probDraw), round(probAwayWin),
                round(homePower), round(awayPower),
                round(lambdaHome), round(lambdaAway),
                round(probOver2_5), round(100.0 - probOver2_5),
                round(probBTTS),
                round(dc1N), round(dcN2), round(dc12),
                kellyHome, kellyDraw, kellyAway, valueSide
        );
    }

    // --- HELPERS MATHÉMATIQUES ---

    private double poisson(int k, double lambda) {
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }

    private long factorial(int n) {
        if (n <= 1) return 1;
        long fact = 1;
        for (int i = 2; i <= n; i++) fact *= i;
        return fact;
    }

    private double applyDixonColes(double prob, int h, int a, double lambda, double mu) {
        double rho = props.getRho(); // Ex: -0.13
        double correction = 1.0;
        if (h == 0 && a == 0) correction = 1.0 - (lambda * mu * rho);
        else if (h == 0 && a == 1) correction = 1.0 + (lambda * rho);
        else if (h == 1 && a == 0) correction = 1.0 + (mu * rho);
        else if (h == 1 && a == 1) correction = 1.0 - rho;
        return Math.max(0, prob * correction);
    }

    private double calculateKelly(double trueProb, Double odds) {
        if (odds == null || odds <= 1.0) return 0.0;
        double b = odds - 1.0;
        double q = 1.0 - trueProb;
        double f = ((b * trueProb) - q) / b;
        return f > 0 ? round((f * 0.25) * 100.0) : 0.0; // Kelly Fractionnaire 1/4
    }

    private double getEloImpact(int homeElo, int awayElo) {
        // Formule ELO standard : P(Win) = 1 / (1 + 10^((Away-Home)/400))
        // Ici on l'adapte pour un multiplicateur de buts
        double diff = homeElo - awayElo;
        // Pour chaque 100 points d'écart, l'équipe forte marque ~15% de plus
        return Math.pow(10, diff / 800.0);
    }

    private double calculateH2HFactor(String homeName, List<MatchAnalysis> h2h) {
        double factor = 0.0;
        int limit = Math.min(h2h.size(), 5);
        for (int i = 0; i < limit; i++) {
            MatchAnalysis m = h2h.get(i);
            if (m.getHomeScore() == null) continue;

            boolean homeWon = (m.getHomeTeam().getName().equals(homeName) && m.getHomeScore() > m.getAwayScore()) ||
                    (m.getAwayTeam().getName().equals(homeName) && m.getAwayScore() > m.getHomeScore());
            if (homeWon) factor += 0.05;
            else factor -= 0.03;
        }
        return factor;
    }

    private double applyContextualFactors(double lambda, boolean isHome, MatchAnalysis match) {
        double factor = 1.0;
        if (isHome) {
            if (match.isHomeKeyPlayerMissing()) factor -= 0.12;
            if (match.isHomeTired()) factor -= 0.08;
        } else {
            if (match.isAwayKeyPlayerMissing()) factor -= 0.12;
            if (match.isAwayNewCoach()) factor += 0.05; // Choc psychologique positif
        }
        return lambda * factor;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
