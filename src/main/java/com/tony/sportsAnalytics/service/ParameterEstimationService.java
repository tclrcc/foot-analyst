package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParameterEstimationService {
    private final TeamRepository teamRepository;
    private static final double XI = 0.0019; // Décroissance temporelle Dixon-Coles

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

                double lambda = point[hIdx + 2] * point[aIdx + n + 2] * gamma;
                double mu = point[aIdx + 2] * point[hIdx + n + 2];

                long days = Math.abs(Duration.between(LocalDateTime.now(), m.getMatchDate()).toDays());
                double weight = Math.exp(-XI * days);

                logL += weight * Math.log(calculateDixonColesProb(m.getHomeScore(), m.getAwayScore(), lambda, mu, rho));
            }

            // Contrainte de normalisation : Moyenne des alphas = 1 (Pénalité si déviation)
            double sumAlpha = 0;
            for(int i=2; i<n+2; i++) sumAlpha += point[i];
            logL -= Math.pow(sumAlpha - n, 2) * 1000;

            return logL;
        };

        SimplexOptimizer optimizer = new SimplexOptimizer(1e-10, 1e-30);
        PointValuePair optimum = optimizer.optimize(
                new MaxEval(20000),
                new ObjectiveFunction(logLikelihood),
                GoalType.MAXIMIZE,
                new InitialGuess(new double[2 * n + 2]), // Initialiser à 1.0 partout
                new NelderMeadSimplex(2 * n + 2)
        );

        // Sauvegarde des nouveaux Alpha/Beta en base
        saveResults(optimum.getPoint(), teams);
    }

    /**
     * Extrait les paramètres optimisés du vecteur 'point' et les sauvegarde pour chaque équipe.
     */
    private void saveResults(double[] point, List<Team> teams) {
        int n = teams.size();
        for (int i = 0; i < n; i++) {
            Team team = teams.get(i);

            // Selon notre vecteur : [0]=gamma, [1]=rho, [2..n+1]=alphas, [n+2..2n+1]=betas
            double alpha = point[i + 2];
            double beta = point[i + n + 2];

            team.setAttackStrength(alpha);
            team.setDefenseStrength(beta);
        }

        // Sauvegarde groupée pour optimiser les performances JPA
        teamRepository.saveAll(teams);
        log.info("✅ Forces d'attaque (Alpha) et défense (Beta) mises à jour en base pour {} équipes.", n);
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
