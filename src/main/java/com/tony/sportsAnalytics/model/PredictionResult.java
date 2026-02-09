package com.tony.sportsAnalytics.model;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResult {
    // Probabilité de victoire (ex: 45.5 %)
    private Double homeWinProbability;
    private Double drawProbability; // On va le simuler simplement pour l'instant
    private Double awayWinProbability;

    // Le score de force calculé par notre algo
    private Double homePowerScore;
    private Double awayPowerScore;
}
