package com.tony.sportsAnalytics.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResult {
    // 1N2 Classique
    private Double homeWinProbability;
    private Double drawProbability;
    private Double awayWinProbability;

    // Scores de puissance (pour affichage comparatif)
    private Double homePowerScore;
    private Double awayPowerScore;

    // --- NOUVELLES STATS AVANCÉES (V6) ---

    // Goals Attendus calculés pour CE match précis
    private Double predictedHomeGoals;
    private Double predictedAwayGoals;

    // Marché des Buts (Over / Under 2.5)
    private Double over2_5_Prob;
    private Double under2_5_Prob;

    // Les deux équipes marquent (BTTS - Both Teams To Score)
    private Double bttsProb;

    // Double Chance
    private Double doubleChance1N; // Dom ou Nul
    private Double doubleChanceN2; // Nul ou Ext
    private Double doubleChance12; // Dom ou Ext
}
