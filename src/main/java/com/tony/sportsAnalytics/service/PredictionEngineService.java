package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionEngineService {

    private final WeatherService weatherService;
    private final AdvancedPredictionService advancedPrediction;
    private final CalibrationService calibrationService;

    // --- CONSTANTES DE SECOURS (Fallback) ---
    // Utilisées uniquement si la Ligue n'a pas encore de paramètres personnalisés en base
    private static final double DEFAULT_WEIGHT_POISSON = 0.55;
    private static final double DEFAULT_MARKET_WEIGHT = 0.30;
    private static final double DEFAULT_RHO = -0.13;

    private static final double ELO_DIVISOR = 400.0;
    private static final double MAX_FINISHING_CORRECTION = 1.25;
    private static final double TIME_DECAY_CONSTANT = 60.0;
    private static final double WEIGHT_H2H = 0.08;

    /**
     * Moteur de prédiction "Ultimate"
     * Orchestre : Dixon-Coles Pro + Elo + Market Anchoring + Calibration + Kelly Risk Management
     */
    public PredictionResult calculateMatchPrediction(MatchAnalysis match,
            List<MatchAnalysis> h2hHistory,
            List<MatchAnalysis> homeHistory,
            List<MatchAnalysis> awayHistory,
            double leagueAvgGoals) {

        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();
        League league = home.getLeague();

        log.info("🔮 Analyse IA : {} vs {}", home.getName(), away.getName());

        // 1. CHARGEMENT DES PARAMÈTRES DYNAMIQUES (Depuis la Ligue)
        double weightPoisson = (league != null && league.getWeightPoisson() != null) ? league.getWeightPoisson() : DEFAULT_WEIGHT_POISSON;
        double weightElo = 1.0 - weightPoisson;
        double marketWeight = (league != null && league.getMarketAnchorWeight() != null) ? league.getMarketAnchorWeight() : DEFAULT_MARKET_WEIGHT;
        // Calcul avantage domicile
        double baseHomeAdv = (league != null) ? league.getHomeAdvantageFactor() : 1.15;
        double homeTeamHomeStrength = calculateHomeDominance(home); // Ratio Pts Dom / Pts Ext
        double finalHomeAdv = baseHomeAdv * homeTeamHomeStrength;

        double rho = (league != null && league.getRho() != null) ? league.getRho() : DEFAULT_RHO;

        // -----------------------------------------------------------
        // 2. MODÈLE ELO (La Fondation Stable)
        // -----------------------------------------------------------
        double eloDiff = home.getEloRating() - away.getEloRating();
        double eloProbHome = 1.0 / (1.0 + Math.pow(10, (-eloDiff - 100.0) / ELO_DIVISOR));
        double eloProbAway = 1.0 / (1.0 + Math.pow(10, (eloDiff + 100.0) / ELO_DIVISOR));

        // -----------------------------------------------------------
        // 3. MODÈLE DIXON-COLES (La Force Instantanée)
        // -----------------------------------------------------------
        double homeLambda = home.getAttackStrength() * away.getDefenseStrength() * finalHomeAdv;
        double awayLambda = away.getAttackStrength() * home.getDefenseStrength();

        // Intégration xG (Correction de la "Chance")
        if (hasXgData(home)) homeLambda = (homeLambda + home.getCurrentStats().getXG()) / 2.0;
        if (hasXgData(away)) awayLambda = (awayLambda + away.getCurrentStats().getXG()) / 2.0;

        // Analyse de Forme & Fatigue
        TeamPerformance homePerf = analyzeTeamPerformance(home, homeHistory, leagueAvgGoals, true, match.getMatchDate());
        TeamPerformance awayPerf = analyzeTeamPerformance(away, awayHistory, leagueAvgGoals, false, match.getMatchDate());

        // Application Finition & Tactique
        homeLambda *= Math.min(homePerf.finishingEfficiency(), MAX_FINISHING_CORRECTION);
        awayLambda *= Math.min(awayPerf.finishingEfficiency(), MAX_FINISHING_CORRECTION);

        homeLambda = applyTacticalOverlay(homeLambda, home, away, awayPerf.volatility());
        awayLambda = applyTacticalOverlay(awayLambda, away, home, homePerf.volatility());

        // Contexte (Joueurs clés, Météo)
        homeLambda = applyContextualFactors(homeLambda, true, match);
        awayLambda = applyContextualFactors(awayLambda, false, match);

        double weatherFactor = calculateWeatherFactor(match);
        homeLambda *= weatherFactor;
        awayLambda *= weatherFactor;

        // H2H Bonus
        if (h2hHistory != null && !h2hHistory.isEmpty()) {
            homeLambda *= (1.0 + calculateH2HFactor(home, h2hHistory));
        }

        // SIMULATION POISSON/WEIBULL PRO
        PredictionResult poissonResult = simulateMatchPro(homeLambda, awayLambda, homePerf, awayPerf, rho);

        // -----------------------------------------------------------
        // 4. HYBRID FUSION (Stats vs Elo)
        // -----------------------------------------------------------
        double rawHome = (poissonResult.getHomeWinProbability() * weightPoisson) + (eloProbHome * 100.0 * weightElo);
        double rawAway = (poissonResult.getAwayWinProbability() * weightPoisson) + (eloProbAway * 100.0 * weightElo);

        // -----------------------------------------------------------
        // 5. MARKET ANCHORING (Sagesse des Foules)
        // -----------------------------------------------------------
        MarketProbs market = calculateMarketImpliedProbs(match.getOdds1(), match.getOddsN(), match.getOdds2());
        if (market != null) {
            // On "tire" la probabilité vers celle du marché selon le poids défini (ex: 30%)
            rawHome = (rawHome * (1.0 - marketWeight)) + (market.home * 100.0 * marketWeight);
            rawAway = (rawAway * (1.0 - marketWeight)) + (market.away * 100.0 * marketWeight);
        }
        // Le nul est toujours le résidu pour garantir la somme à 100%
        double rawDraw = 100.0 - rawHome - rawAway;

        // -----------------------------------------------------------
        // 6. CALIBRATION SIGMOÏDE (Le "Reality Check")
        // -----------------------------------------------------------
        double finalProbHome = rawHome;
        double finalProbAway = rawAway;
        double finalProbDraw = rawDraw;

        if (league != null && league.getCalibrationA() != null) {
            finalProbHome = calibrationService.calibrate(rawHome, league.getCalibrationA(), league.getCalibrationB());
            finalProbAway = calibrationService.calibrate(rawAway, league.getCalibrationA(), league.getCalibrationB());
            // Recalcul du nul pour normaliser
            finalProbDraw = 100.0 - finalProbHome - finalProbAway;
        }

        // -----------------------------------------------------------
        // 7. GESTION DU RISQUE (KELLY AJUSTÉ)
        // -----------------------------------------------------------
        // Facteur de confiance basé sur la volatilité du match (Chaos)
        double confidenceFactor = calculateConfidenceFactor(homePerf.volatility(), awayPerf.volatility());

        double kellyHome = calculateAdjustedKelly(finalProbHome / 100.0, match.getOdds1(), confidenceFactor);
        double kellyDraw = calculateAdjustedKelly(finalProbDraw / 100.0, match.getOddsN(), confidenceFactor);
        double kellyAway = calculateAdjustedKelly(finalProbAway / 100.0, match.getOdds2(), confidenceFactor);

        // 1. Génération du Prompt IA
        String aiPrompt = generateAiPrompt(
                match, home, away, homePerf, awayPerf,
                finalProbHome, finalProbDraw, finalProbAway,
                kellyHome, kellyAway,
                poissonResult,              // On passe le résultat de la simulation (contenant xG, BTTS...)
                round(confidenceFactor * 100.0) // On passe le score de confiance calculé
        );

        // 2. Logs structurés pour l'utilisateur (HTML formatted)
        String userLogs = generateUserLogs(home, away, eloDiff, homePerf, awayPerf, poissonResult, confidenceFactor);
        match.setMyNotes(userLogs); // On sauvegarde les logs dans myNotes

        return PredictionResult.builder()
                .homeWinProbability(round(finalProbHome))
                .drawProbability(round(finalProbDraw))
                .awayWinProbability(round(finalProbAway))

                .predictedHomeGoals(round(homeLambda))
                .predictedAwayGoals(round(awayLambda))

                // Scores de puissance (Attack Strength + Forme récente)
                .homePowerScore(round(home.getAttackStrength() * 10 + homePerf.attackRating()))
                .awayPowerScore(round(away.getAttackStrength() * 10 + awayPerf.attackRating()))

                // Probabilités secondaires issues de la simulation
                .over2_5_Prob(poissonResult.getOver2_5_Prob())
                .under2_5_Prob(poissonResult.getUnder2_5_Prob()) // Ajouté pour complétude
                .bttsProb(poissonResult.getBttsProb())
                .probBTTS_No(poissonResult.getProbBTTS_No())
                .probUnder1_5(poissonResult.getProbUnder1_5())
                .probOver3_5(poissonResult.getProbOver3_5())
                .exactScore(poissonResult.getExactScore())
                .exactScoreProb(poissonResult.getExactScoreProb())

                // Stratégie de mise (Kelly)
                .kellyStakeHome(kellyHome)
                .kellyStakeDraw(kellyDraw)
                .kellyStakeAway(kellyAway)
                .valueBetDetected(detectValueBet(kellyHome, kellyDraw, kellyAway))

                // Champs scores (simulation)
                .homeScoreOver0_5(poissonResult.getHomeScoreOver0_5())
                .homeScoreOver1_5(poissonResult.getHomeScoreOver1_5())
                .awayScoreOver0_5(poissonResult.getAwayScoreOver0_5())
                .awayScoreOver1_5(poissonResult.getAwayScoreOver1_5())

                // Doubles chances (calculées sur les probas finales calibrées)
                .doubleChance1N(round(finalProbHome + finalProbDraw))
                .doubleChanceN2(round(finalProbDraw + finalProbAway))
                .doubleChance12(round(finalProbHome + finalProbAway))

                // Mises Kelly secondaires (Exemple, à affiner si besoin)
                .kellyOver2_5(calculateAdjustedKelly(poissonResult.getOver2_5_Prob()/100.0, match.getOddsOver25(), confidenceFactor))
                .kellyBTTS(calculateAdjustedKelly(poissonResult.getBttsProb()/100.0, match.getOddsBTTSYes(), confidenceFactor))
                .aiAnalysisPrompt(aiPrompt)
                .matchVolatility(round((homePerf.volatility() + awayPerf.volatility()) / 2.0))
                .confidenceScore(round(confidenceFactor * 100.0))

                .build();
    }

    private double calculateHomeDominance(Team t) {
        // 1. Vérification de base : Stats existantes ?
        if (t.getCurrentStats() == null) return 1.0;

        // 2. Extraction sécurisée des valeurs (gestion des nulls)
        Integer mpHome = t.getCurrentStats().getMatchesPlayedHome();
        Integer mpAway = t.getCurrentStats().getMatchesPlayedAway();
        Integer pts = t.getCurrentStats().getPoints();
        Integer venuePts = t.getCurrentStats().getVenuePoints();

        // Si une donnée essentielle manque ou est à 0, on retourne la valeur neutre
        if (mpHome == null || mpHome == 0 || mpAway == null || mpAway == 0 ||
                pts == null || venuePts == null) {
            return 1.0;
        }

        // 3. Calculs sécurisés (tout est garanti non-null ici)
        double ptsHomePerGame = (double) venuePts / mpHome;
        double ptsAwayPerGame = (double) (pts - venuePts) / mpAway;

        if (ptsAwayPerGame < 0.1) return 1.2; // Cas rare : ne gagne jamais à l'extérieur (éviter division par ~0)

        // 4. Ratio et bornage
        double ratio = ptsHomePerGame / ptsAwayPerGame;
        return Math.max(0.8, Math.min(1.3, ratio)); // Clamp pour éviter les valeurs extrêmes
    }

    private String generateUserLogs(Team h, Team a, double eloDiff, TeamPerformance hp, TeamPerformance ap, PredictionResult sim, double conf) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ul>");
        sb.append(String.format("<li><strong>Analyse de Forme :</strong> %s est à %.0f%% de potentiel offensif, %s à %.0f%%.</li>",
                h.getName(), hp.attackRating()*100, a.getName(), ap.attackRating()*100));

        sb.append(String.format("<li><strong>Stabilité du Match :</strong> Indice de confiance calculé à <strong>%.0f%%</strong> (Volatilité: %.2f).</li>",
                conf*100, (hp.volatility() + ap.volatility())/2));

        if (Math.abs(eloDiff) > 100) {
            sb.append(String.format("<li><strong>Hiérarchie :</strong> %s est nettement supérieur au classement Elo (%+.0f pts).</li>",
                    eloDiff > 0 ? h.getName() : a.getName(), eloDiff));
        }

        if (sim.getBttsProb() > 60) {
            sb.append("<li><strong>Style de Jeu :</strong> Forte probabilité de buts des deux côtés (Défenses perméables détectées).</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    // Modifiez la signature pour accepter les résultats de simulation (simResult) et la confiance (conf)
    private String generateAiPrompt(MatchAnalysis m, Team h, Team a, TeamPerformance hp, TeamPerformance ap,
            double p1, double pN, double p2, double k1, double k2,
            PredictionResult simResult, double conf) {

        // On n'utilise PLUS m.getPrediction() (qui est l'ancienne valeur ou null)
        // On utilise simResult qui contient les stats calculées à l'instant T (xG, BTTS, etc.)

        String contextAlerts = "";
        if (m.isAwayNewCoach()) contextAlerts += "- ATTENTION : Choc psychologique potentiel (Nouveau Coach Extérieur)\n";

        return String.format(
                "Agis comme un expert en paris sportifs et modélisation statistique.\n" +
                        "Analyse ce match de %s en te basant exclusivement sur les données mathématiques fournies.\n\n" +

                        "--- 1. IDENTITÉ DU MATCH ---\n" +
                        "MATCH : %s (Dom) vs %s (Ext)\n" +
                        "DATE : %s\n" +
                        "ARBITRE : %s\n" +
                        "%s\n" +

                        "--- 2. PROFILS STATISTIQUES (Saison & Forme) ---\n" +
                        "FORCES INTRINSÈQUES (Alpha/Beta - Dixon Coles) :\n" +
                        "- %s : Attaque %.2f / Défense %.2f\n" +
                        "- %s : Attaque %.2f / Défense %.2f\n\n" +

                        "DYNAMIQUE RÉCENTE (Ratio Performance vs Moyenne Ligue) :\n" +
                        "- %s : %.2f %s\n" +
                        "- %s : %.2f %s\n" +
                        "(Note : > 1.0 = Surperforme, < 0.8 = En difficulté offensive)\n\n" +

                        "--- 3. PRÉDICTIONS DE L'ALGORITHME ---\n" +
                        "PROBABILITÉS PURES : Home %.1f%% | Draw %.1f%% | Away %.1f%%\n" +
                        "EXPECTED GOALS : %.2f - %.2f\n" +
                        "SCORE EXACT LE PLUS PROBABLE : %s\n\n" +

                        "INDICATEURS DE RISQUE :\n" +
                        "- Volatilité du match : %.2f (0.0=Stable, >1.0=Chaotique)\n" +
                        "- Confiance du modèle : %.0f%%\n" +
                        "- BTTS (Les 2 marquent) : %.1f%% | Over 2.5 Buts : %.1f%%\n\n" +

                        "--- 4. ANALYSE DE MARCHÉ (VALUE) ---\n" +
                        "COTES BOOKMAKER : 1@%.2f | N@%.2f | 2@%.2f\n" +
                        "CONSEIL DE MISE (Kelly Criterion) : Home %.1f%% | Away %.1f%%\n\n" +

                        "--- TA MISSION ---\n" +
                        "1. Analyse le rapport de force : Est-ce que la forme récente contredit la puissance intrinsèque des équipes ?\n" +
                        "2. Évalue la 'Value' : Mon algorithme suggère une mise sur %s. Est-ce cohérent avec les statistiques de buts (xG) ?\n" +
                        "3. Donne ton verdict final : Suivre, Réduire la mise, ou Passer ?",

                h.getLeague().getName(),
                h.getName(), a.getName(),
                m.getMatchDate(),
                (m.getReferee() != null ? m.getReferee() : "Non spécifié"),
                contextAlerts,

                h.getName(), h.getAttackStrength(), h.getDefenseStrength(),
                a.getName(), a.getAttackStrength(), a.getDefenseStrength(),

                h.getName(), hp.attackRating(), (hp.attackRating() < 0.8 ? "(⚠️ MÉFORME)" : ""),
                a.getName(), ap.attackRating(), (ap.attackRating() < 0.8 ? "(⚠️ MÉFORME)" : ""),

                p1, pN, p2,
                // Utilisation de simResult au lieu de pred
                simResult.getPredictedHomeGoals(), simResult.getPredictedAwayGoals(), simResult.getExactScore(),

                (hp.volatility() + ap.volatility())/2.0,
                conf, // Utilisation de la variable passée en paramètre (plus de null possible)
                simResult.getBttsProb(), simResult.getOver2_5_Prob(),

                m.getOdds1(), m.getOddsN(), m.getOdds2(),
                k1, k2,
                (k1 > 0 ? "le DOMICILE" : (k2 > 0 ? "l'EXTÉRIEUR" : "le NUL"))
        );
    }

    // --- MÉTHODES MÉTIERS AVANCÉES ---

    private boolean hasXgData(Team t) {
        return t.getCurrentStats() != null && t.getCurrentStats().getXG() != null;
    }

    /**
     * KELLY FRACTIONNAIRE AJUSTÉ À LA VOLATILITÉ
     * Si les équipes sont instables (volatilité élevée), on divise la mise conseillée.
     * C'est la clé pour préserver la bankroll sur le long terme.
     */
    private double calculateAdjustedKelly(double prob, Double odds, double confidenceFactor) {
        if (odds == null || odds <= 1.0) return 0.0;
        double b = odds - 1.0;
        double q = 1.0 - prob;
        double f = ((b * prob) - q) / b;

        // On réduit la fraction à 0.10 (1/10th Kelly) pour survivre à la variance
        double stake = f * 0.10 * confidenceFactor;
        return stake > 0 ? round(stake * 100.0) : 0.0;
    }

    private double calculateConfidenceFactor(double volHome, double volAway) {
        double avgVol = (volHome + volAway) / 2.0;
        // Si volatilité normale (0.5), facteur ~1.0. Si chaos (1.5), facteur ~0.6
        // On ne descend jamais sous 30% de confiance si la Value est là.
        return Math.max(0.3, 1.0 - (avgVol * 0.4));
    }

    private String detectValueBet(double k1, double kN, double k2) {
        // On monte le seuil à 5.0 pour être encore plus élitiste sur les sélections
        if (k1 > 5.0) return "HOME";
        if (k2 > 5.0) return "AWAY";
        if (kN > 3.5) return "DRAW";
        return null;
    }

    /**
     * Simulation Dixon-Coles avec correction "Draw Killer"
     */
    private PredictionResult simulateMatchPro(double lambdaHome, double lambdaAway,
            TeamPerformance hPerf, TeamPerformance aPerf, double rho) {
        double probHome = 0.0, probDraw = 0.0, probAway = 0.0;
        double probOver25 = 0.0, probBTTS = 0.0, probBTTS_No = 0.0;
        double probUnder1_5 = 0.0, probUnder2_5 = 0.0, probOver3_5 = 0.0;
        double probHomeOver05 = 0.0, probHomeOver15 = 0.0;
        double probAwayOver05 = 0.0, probAwayOver15 = 0.0;

        double maxProb = -1.0;
        String exactScore = "0-0";

        // Game Chaos : Moyenne de la volatilité des équipes
        double gameChaos = (hPerf.volatility() + aPerf.volatility()) / 2.0;

        for (int h = 0; h <= 9; h++) {
            for (int a = 0; a <= 9; a++) {
                // Calcul probabilité score exact avec correction de dépendance (Rho)
                double p = advancedPrediction.calculateProbability(h, a, lambdaHome, lambdaAway, rho);

                // AJUSTEMENT DIAGONAL (Le "Draw Killer")
                // Les modèles Poisson sous-estiment souvent les 0-0 et 1-1 dans les matchs fermés et peu chaotiques
                if (h == a && (h + a) <= 2 && gameChaos < 0.4) {
                    p *= 1.10; // Boost Nul si match très stable
                }

                // Accumulation
                if (h > a) probHome += p;
                else if (h == a) probDraw += p;
                else probAway += p;

                int totalGoals = h + a;
                if (totalGoals > 2.5) probOver25 += p;
                else probUnder2_5 += p; // else implicite

                if (totalGoals < 1.5) probUnder1_5 += p;
                if (totalGoals > 3.5) probOver3_5 += p;

                if (h > 0 && a > 0) probBTTS += p; else probBTTS_No += p;

                if (h > 0) probHomeOver05 += p; if (h > 1) probHomeOver15 += p;
                if (a > 0) probAwayOver05 += p; if (a > 1) probAwayOver15 += p;

                if (p > maxProb) { maxProb = p; exactScore = h + "-" + a; }
            }
        }

        // Normalisation (au cas où le Draw Killer ait dépassé 1.0, peu probable mais safe)
        double total = probHome + probDraw + probAway;
        if (total == 0) total = 1.0;

        return PredictionResult.builder()
                .homeWinProbability(round((probHome/total)*100))
                .drawProbability(round((probDraw/total)*100))
                .awayWinProbability(round((probAway/total)*100))
                .over2_5_Prob(round((probOver25/total)*100))
                .under2_5_Prob(round((probUnder2_5/total)*100))
                .bttsProb(round((probBTTS/total)*100))
                .probBTTS_No(round((probBTTS_No/total)*100))
                .probUnder1_5(round((probUnder1_5/total)*100))
                .probOver3_5(round((probOver3_5/total)*100))
                .homeScoreOver0_5(round((probHomeOver05/total)*100))
                .homeScoreOver1_5(round((probHomeOver15/total)*100))
                .awayScoreOver0_5(round((probAwayOver05/total)*100))
                .awayScoreOver1_5(round((probAwayOver15/total)*100))
                .exactScore(exactScore)
                .exactScoreProb(round((maxProb/total)*100))
                .build();
    }

    // --- ANALYSE DE FORME ---
    private TeamPerformance analyzeTeamPerformance(Team team, List<MatchAnalysis> history, double leagueAvg, boolean isHomeAnalysis, LocalDateTime targetDate) {
        if (history == null || history.isEmpty()) return new TeamPerformance(1.0, 1.0, 1.0, 0.5, 1.0);

        int limit = Math.min(history.size(), 10);

        // CORRECTION : Réintroduction de sumXgAgainst
        double sumXgFor = 0.0, sumXgAgainst = 0.0;
        double sumGoalsFor = 0.0;
        double totalWeight = 0.0, volatilitySum = 0.0;

        for (int i = 0; i < limit; i++) {
            MatchAnalysis m = history.get(i);
            if (m.getMatchDate() == null) continue;

            long daysAgo = Math.abs(Duration.between(targetDate, m.getMatchDate()).toDays());
            double timeWeight = Math.exp(-daysAgo / TIME_DECAY_CONSTANT);
            boolean wasHome = m.getHomeTeam().equals(team);
            double contextWeight = (wasHome == isHomeAnalysis) ? 1.10 : 0.90;
            double finalWeight = timeWeight * contextWeight;

            int goalsF = safeInt(wasHome ? m.getHomeScore() : m.getAwayScore());
            int goalsA = safeInt(wasHome ? m.getAwayScore() : m.getHomeScore());

            // --- Extraction sécurisée des xG ---
            MatchDetailStats myStats = wasHome ? m.getHomeMatchStats() : m.getAwayMatchStats();
            MatchDetailStats oppStats = wasHome ? m.getAwayMatchStats() : m.getHomeMatchStats();

            Double rawXgF = (myStats != null) ? myStats.getXG() : null;
            double xgF = (rawXgF != null) ? rawXgF : 0.0;
            if (xgF == 0.0) xgF = Math.max(0.2, goalsF * 0.8);

            Double rawXgA = (oppStats != null) ? oppStats.getXG() : null;
            double xgA = (rawXgA != null) ? rawXgA : 0.0;
            if (xgA == 0.0) xgA = Math.max(0.2, goalsA * 0.8);
            // -----------------------------------

            sumXgFor += xgF * finalWeight;
            sumXgAgainst += xgA * finalWeight; // CORRECTION : xgA est maintenant utilisé
            sumGoalsFor += goalsF * finalWeight;

            // Volatilité : On prend en compte l'écart Offensif ET Défensif
            // Si l'équipe concède beaucoup plus (ou moins) de buts que prévu (xgA), elle est instable.
            double matchVolatility = Math.abs(xgF - goalsF) + Math.abs(xgA - goalsA);
            volatilitySum += matchVolatility * finalWeight;

            totalWeight += finalWeight;

            // RECUPERATION DE LA FORCE DE L'ADVERSAIRE DE L'ÉPOQUE
            Team opponent = wasHome ? m.getAwayTeam() : m.getHomeTeam();
            double oppDefense = opponent.getDefenseStrength(); // La valeur Alpha/Beta actuelle

            // AJUSTEMENT : Une performance contre une grosse défense vaut plus cher
            // Si l'adversaire a une Def=0.80 (Encaisse peu), et que je mets 2.0 xG -> C'est comme mettre 2.5 xG contre une équipe moyenne.
            double adjustedXgF = xgF * (1.0 + (1.0 - oppDefense));

            // Idem pour la défense : Si je prends 1.0 xG contre une Attaque=1.40 (Forte) -> J'ai bien défendu.
            double oppAttack = opponent.getAttackStrength();
            double adjustedXgA = xgA * (1.0 - (oppAttack - 1.0));

            sumXgFor += adjustedXgF * finalWeight;
            sumXgAgainst += adjustedXgA * finalWeight;
        }

        if (totalWeight == 0) return new TeamPerformance(1.0, 1.0, 1.0, 0.5, 1.0);

        double avgXgFor = sumXgFor / totalWeight;
        double avgXgAgainst = sumXgAgainst / totalWeight;

        double attackRating = (leagueAvg > 0) ? (avgXgFor / leagueAvg) : 1.0;
        double defenseRating = (leagueAvg > 0) ? (avgXgAgainst / leagueAvg) : 1.0;
        double finishing = (avgXgFor > 0.2) ? (sumGoalsFor/totalWeight / (sumXgFor/totalWeight)) : 1.0;

        // Normalisation de la volatilité
        double volatility = (volatilitySum / totalWeight) / (leagueAvg);

        return new TeamPerformance(attackRating, defenseRating, finishing, 0.5, volatility);
    }

    private double calculateH2HFactor(Team currentHomeTeam, List<MatchAnalysis> h2h) {
        double factor = 0.0;
        int limit = Math.min(h2h.size(), 10);

        for (int i = 0; i < limit; i++) {
            MatchAnalysis m = h2h.get(i);
            if (m.getHomeScore() == null) continue;

            double weight = (double)(limit - i) / limit;
            boolean homeWonMatch = m.getHomeScore() > m.getAwayScore();
            boolean awayWonMatch = m.getAwayScore() > m.getHomeScore();

            if (m.getHomeTeam().equals(currentHomeTeam) && homeWonMatch) {
                factor += (WEIGHT_H2H * weight);
            } else if (m.getAwayTeam().equals(currentHomeTeam) && awayWonMatch) {
                factor += (WEIGHT_H2H * weight * 1.2);
            } else if ((m.getHomeTeam().equals(currentHomeTeam) && awayWonMatch) ||
                    (m.getAwayTeam().equals(currentHomeTeam) && homeWonMatch)) {
                factor -= (WEIGHT_H2H * weight);
            }
        }
        return factor;
    }

    private double applyTacticalOverlay(double lambda, Team team, Team opponent, double opponentVolatility) {
        double factor = 1.0;
        TeamStats stats = team.getCurrentStats();
        TeamStats oppStats = opponent.getCurrentStats();

        if (stats != null && oppStats != null) {
            // Field Tilt (Domination) vs Défense perméable
            if (stats.getFieldTilt() != null && stats.getFieldTilt() > 55.0) {
                double vulnerabilityBonus = (oppStats.getGoalsAgainst() != null && oppStats.getGoalsAgainst() > 1.5) ? 0.08 : 0.04;
                factor += vulnerabilityBonus;
            }
            // Pressing (PPDA) vs Chaos
            if (stats.getPpda() != null && stats.getPpda() < 10.0) {
                double pressingEffect = (opponentVolatility > 1.2) ? 0.07 : 0.03;
                factor += pressingEffect;
            }
            // Contre-Attaque (Si je subis mais que j'ai une grosse attaque)
            if (stats.getPpda() != null && stats.getPpda() > 15.0 && team.getAttackStrength() > 1.1) {
                factor += 0.05;
            }
        }
        return lambda * factor;
    }

    private double applyContextualFactors(double lambda, boolean isHome, MatchAnalysis m) {
        double factor = 1.0;
        double impactScore = isHome ? m.getHomeMissingImpactScore() : m.getAwayMissingImpactScore();
        if (impactScore > 0) factor -= (impactScore * 0.25);
        if (isHome && m.isHomeTired()) factor -= 0.05;
        if (!isHome && m.isAwayNewCoach()) factor += 0.05;
        return lambda * factor;
    }

    private double calculateWeatherFactor(MatchAnalysis match) {
        if (match.getMatchDate().isBefore(LocalDateTime.now())) return 1.0;
        return weatherService.getMatchWeather(match.getHomeTeam().getLatitude(), match.getHomeTeam().getLongitude(), match.getMatchDate().toString())
                .map(w -> w.windSpeed() > 30.0 ? 0.90 : 1.0).orElse(1.0);
    }

    private MarketProbs calculateMarketImpliedProbs(Double o1, Double oN, Double o2) {
        if (o1 == null || oN == null || o2 == null) return null;
        double rawSum = (1.0 / o1) + (1.0 / oN) + (1.0 / o2);
        return new MarketProbs((1.0 / o1) / rawSum, (1.0 / oN) / rawSum, (1.0 / o2) / rawSum);
    }

    private int safeInt(Integer val) { return val == null ? 0 : val; }
    private double round(double val) { return Math.round(val * 100.0) / 100.0; }

    // --- RECORDS INTERNES ---
    public record TeamPerformance(double attackRating, double defenseRating, double finishingEfficiency, double dominance, double volatility) {}
    private record MarketProbs(double home, double draw, double away) {}
}
