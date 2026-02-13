package com.tony.sportsAnalytics.model;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.ArrayList;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionResult {
    // 1N2
    private Double homeWinProbability;
    private Double drawProbability;
    private Double awayWinProbability;

    // Power Scores & Lambdas
    private Double homePowerScore;
    private Double awayPowerScore;
    private Double predictedHomeGoals;
    private Double predictedAwayGoals;

    // --- MARCHÉS ALTERNATIFS (OVER/UNDER & BTTS) ---
    private Double probOver1_5;
    private Double probUnder1_5;
    private Double over2_5_Prob;
    private Double under2_5_Prob;
    private Double probOver3_5;

    private Double bttsProb;
    private Double probBTTS_No;

    // Double Chance
    private Double doubleChance1N;
    private Double doubleChanceN2;
    private Double doubleChance12;

    // Score Exact
    private String exactScore;
    private Double exactScoreProb;

    // Métriques de performance de l'algo
    private Boolean predictionCorrect;
    private Double brierScore;

    // Informations contextuelles
    @Column(columnDefinition = "TEXT")
    private String aiAnalysisPrompt;
    private Double matchVolatility;
    private Double confidenceScore;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "prediction_insights", joinColumns = @JoinColumn(name = "match_analysis_id"))
    @Column(name = "insight")
    @Builder.Default
    private List<String> keyFacts = new ArrayList<>();
}
