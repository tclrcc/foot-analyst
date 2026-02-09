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
    // 1. Probabilités 1N2
    private Double homeWinProbability;
    private Double drawProbability;
    private Double awayWinProbability;

    // 2. Scores de Puissance (Legacy)
    private Double homePowerScore;
    private Double awayPowerScore;

    // 3. Buts Attendus (xG du match)
    private Double predictedHomeGoals;
    private Double predictedAwayGoals;

    // 4. Marché des Buts
    private Double over2_5_Prob;
    private Double under2_5_Prob;
    private Double bttsProb;

    // 5. Double Chance
    private Double doubleChance1N; // Dom ou Nul
    private Double doubleChanceN2; // Nul ou Ext
    private Double doubleChance12; // Dom ou Ext

    // --- V9 : GESTION FINANCIÈRE (KELLY) ---
    // Ces champs doivent être ajoutés DANS CET ORDRE pour correspondre à ton "return new PredictionResult(...)"
    private Double kellyStakeHome;    // % Bankroll sur 1
    private Double kellyStakeDraw;    // % Bankroll sur N
    private Double kellyStakeAway;    // % Bankroll sur 2

    private String valueBetDetected;  // "HOME", "DRAW", "AWAY" ou null
}
