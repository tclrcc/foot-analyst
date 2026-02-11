package com.tony.sportsAnalytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service de calculs statistiques avancés pour le modèle Dixon-Coles.
 * Ce service est purement mathématique et stateless.
 */
@Service
@RequiredArgsConstructor
public class AdvancedPredictionService {

    /**
     * Calcule la probabilité exacte d'un score (x, y) en intégrant la correction de corrélation de Dixon-Coles.
     * @param x      Buts équipe domicile
     * @param y      Buts équipe extérieur
     * @param lambda Espérance de buts domicile (moyennée Dixon-Coles/xG/Elo)
     * @param mu     Espérance de buts extérieur
     * @param rho    Paramètre de corrélation (généralement ~ -0.13)
     * @return       Probabilité entre 0 et 1
     */
    public double calculateProbability(int x, int y, double lambda, double mu, double rho) {
        // Probabilité de Poisson indépendante de base
        double poissonProb = (poisson(x, lambda) * poisson(y, mu));

        // Application de la fonction de correction Tau pour les faibles scores (Dixon-Coles 1997)
        double tau = 1.0;
        if (x == 0 && y == 0) tau = 1 - (lambda * mu * rho);
        else if (x == 0 && y == 1) tau = 1 + (lambda * rho);
        else if (x == 1 && y == 0) tau = 1 + (mu * rho);
        else if (x == 1 && y == 1) tau = 1 - rho;

        return Math.max(0, poissonProb * tau);
    }

    /**
     * Loi de Poisson : P(k; λ) = (λ^k * e^-λ) / k!
     */
    private double poisson(int k, double lambda) {
        if (lambda <= 0) return k == 0 ? 1.0 : 0.0;
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }

    /**
     * Calcul de la factorielle (version itérative pour éviter stack overflow)
     */
    private long factorial(int n) {
        if (n <= 1) return 1;
        long res = 1;
        for (int i = 2; i <= n; i++) {
            res *= i;
        }
        return res;
    }
}
