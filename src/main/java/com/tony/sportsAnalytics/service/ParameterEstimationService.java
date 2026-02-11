package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParameterEstimationService {
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final MatchAnalysisRepository matchAnalysisRepository;

    private static final double XI = 0.0019; // Décroissance temporelle Dixon-Coles

    // N'oubliez pas l'import de @Transactional
    @Transactional
    public void runEstimationForLeague(String leagueIdStr) { // On utilise l'ID ou le Code selon votre logique
        // Astuce : On récupère la ligue via une des équipes ou un repo de ligue
        // Ici on suppose qu'on passe l'ID de la ligue (ex: 1 pour PL)
        Long leagueId = Long.parseLong(leagueIdStr);

        // 1. Récupérer les matchs historiques
        List<MatchAnalysis> matches = matchAnalysisRepository.findFinishedMatchesByLeague(leagueId);
        if (matches.isEmpty()) {
            log.warn("Aucun match trouvé pour la ligue ID {}", leagueId);
            return;
        }

        // 2. Récupérer les équipes uniques de ces matchs
        List<Team> teams = matches.stream()
                .map(MatchAnalysis::getHomeTeam)
                .distinct()
                .toList();

        log.info("🧮 Début de l'estimation MLE pour {} équipes sur {} matchs...", teams.size(), matches.size());

        // 3. Lancer l'optimisation (Votre méthode existante)
        estimateParameters(matches, teams);
    }

    public void estimateParameters(List<MatchAnalysis> matches, List<Team> teams) {
        int n = teams.size();
        Map<Long, Integer> teamIdx = new HashMap<>();
        for (int i = 0; i < n; i++) teamIdx.put(teams.get(i).getId(), i);

        // Vecteur : [0] gamma (dom), [1] rho (corr), [2..n+1] alphas, [n+2..2n+1] betas
        MultivariateFunction logLikelihood = point -> {
            double gamma = point[0];
            double rho = point[1];
            double logL = 0;

            for (MatchAnalysis m : matches) {
                if (m.getHomeScore() == null) continue;

                int hIdx = teamIdx.get(m.getHomeTeam().getId());
                int aIdx = teamIdx.get(m.getAwayTeam().getId());

                // Protection contre les valeurs négatives ou nulles aberrantes
                double alphaHome = Math.max(0.01, point[hIdx + 2]);
                double betaAway = Math.max(0.01, point[aIdx + n + 2]);
                double alphaAway = Math.max(0.01, point[aIdx + 2]);
                double betaHome = Math.max(0.01, point[hIdx + n + 2]);

                double lambda = alphaHome * betaAway * gamma;
                double mu = alphaAway * betaHome;

                long days = Math.abs(Duration.between(LocalDateTime.now(), m.getMatchDate()).toDays());
                double weight = Math.exp(-XI * days);

                logL += weight * Math.log(calculateDixonColesProb(m.getHomeScore(), m.getAwayScore(), lambda, mu, rho));
            }

            // Contrainte de normalisation : Moyenne des alphas = 1
            double sumAlpha = 0;
            for(int i=2; i<n+2; i++) sumAlpha += point[i];
            logL -= Math.pow(sumAlpha - n, 2) * 1000;

            return logL;
        };

        // --- CORRECTION DU DÉMARRAGE (INITIAL GUESS) ---
        double[] initialGuess = new double[2 * n + 2];
        Arrays.fill(initialGuess, 1.0); // On commence avec Alpha/Beta à 1.0 (neutre)
        initialGuess[0] = 1.20; // Gamma (Avantage dom) commence à 1.20
        initialGuess[1] = -0.1; // Rho commence légèrement négatif

        // --- AUGMENTATION DE LA LIMITE (MaxEval) ---
        SimplexOptimizer optimizer = new SimplexOptimizer(1e-10, 1e-30);
        PointValuePair optimum = optimizer.optimize(
                new MaxEval(200000), // Augmenté de 20k à 200k
                new ObjectiveFunction(logLikelihood),
                GoalType.MAXIMIZE,
                new InitialGuess(initialGuess), // Utilisation du point de départ optimisé
                new NelderMeadSimplex(2 * n + 2)
        );

        saveResults(optimum.getPoint(), teams, teams.getFirst().getLeague());
    }

    /**
     * Extrait les paramètres optimisés du vecteur 'point' et les sauvegarde pour chaque équipe.
     */
    private void saveResults(double[] point, List<Team> teams, League league) {
        // Selon le vecteur : [0]=gamma, [1]=rho
        league.setHomeAdvantageFactor(point[0]);
        league.setRho(point[1]);

        // Alphas et Betas pour chaque équipe
        int n = teams.size();
        for (int i = 0; i < n; i++) {
            Team team = teams.get(i);
            team.setAttackStrength(point[i + 2]);
            team.setDefenseStrength(point[i + n + 2]);
        }

        leagueRepository.save(league);
        teamRepository.saveAll(teams);
        log.info("✅ Paramètres Dixon-Coles (Rho: {}, Gamma: {}) mis à jour pour la ligue {}",
                league.getRho(), league.getHomeAdvantageFactor(), league.getName());
    }

    /**
     * Calcul itératif de la factorielle pour éviter les dépassements de pile (StackOverflow).
     */
    private long factorial(int n) {
        if (n <= 1) return 1;
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    private double calculateDixonColesProb(int x, int y, double l, double m, double r) {
        // Probabilité de Poisson standard
        double poissonProb = (Math.pow(l, x) * Math.exp(-l) / factorial(x)) * (Math.pow(m, y) * Math.exp(-m) / factorial(y));

        // Application de la correction Tau pour les scores 0-0, 1-0, 0-1 et 1-1
        double tau = 1.0;
        if (x == 0 && y == 0) tau = 1 - (l * m * r);
        else if (x == 0 && y == 1) tau = 1 + (l * r);
        else if (x == 1 && y == 0) tau = 1 + (m * r);
        else if (x == 1 && y == 1) tau = 1 - r;

        return Math.max(1e-10, poissonProb * tau);
    }
}
