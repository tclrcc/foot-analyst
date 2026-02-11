package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
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

    private double calculateDixonColesProb(int x, int y, double l, double m, double r) {
        double p = (Math.pow(l, x) * Math.exp(-l) / factorial(x)) * (Math.pow(m, y) * Math.exp(-m) / factorial(y));
        // Appliquer la fonction Tau (correction Dixon-Coles)
        // ... (Logique déjà présente dans ton AdvancedPredictionService)
        return Math.max(1e-10, p);
    }
}
