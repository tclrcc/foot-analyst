package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    // Power Scores
    private Double homePowerScore;
    private Double awayPowerScore;

    // Lambdas (Espérance)
    private Double predictedHomeGoals;
    private Double predictedAwayGoals;

    // Marchés Globaux
    private Double over2_5_Prob;
    private Double under2_5_Prob;
    private Double bttsProb;

    // Double Chance
    private Double doubleChance1N;
    private Double doubleChanceN2;
    private Double doubleChance12;

    // Kelly 1N2
    private Double kellyStakeHome;
    private Double kellyStakeDraw;
    private Double kellyStakeAway;
    private String valueBetDetected;

    // --- V15 : NOUVELLES MÉTRIQUES ---

    // Probabilités Buts Équipe Domicile
    private Double homeScoreOver0_5; // Proba que Home marque au moins 1
    private Double homeScoreOver1_5; // Proba que Home marque au moins 2

    // Probabilités Buts Équipe Extérieur
    private Double awayScoreOver0_5;
    private Double awayScoreOver1_5;

    // Kelly Marchés Buts
    private Double kellyOver1_5; // Value sur +1.5 buts match ?
    private Double kellyOver2_5; // Value sur +2.5 buts match ?
    private Double kellyBTTS;    // Value sur Les 2 marquent ?

    // Score Exact le plus probable
    private String exactScore;   // Ex: "2-1"
    private Double exactScoreProb; // Ex: 12.5 (%)

    private Double probUnder1_5;
    private Double probUnder2_5;
    private Double probOver3_5;
    private Double probBTTS_No;

    private Boolean predictionCorrect; // Vrai si le résultat final avait la plus haute proba (ou >35%)
    private Double brierScore;         // Score de précision (plus bas = meilleur)
    private Double rpsScore;           // Ranked Probability Score (encore plus précis)

    @Column(columnDefinition = "TEXT")
    private String aiAnalysisPrompt; // Le texte complet pour ChatGPT/Gemini
    private Double matchVolatility;  // Niveau de chaos (0.0 à 2.0)
    private Double confidenceScore;  // Confiance de l'algo (0 à 100%)

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "prediction_insights", joinColumns = @JoinColumn(name = "match_analysis_id"))
    @Column(name = "insight")
    @Builder.Default // Indique à Lombok d'utiliser l'ArrayList par défaut lors du build()
    private List<String> keyFacts = new ArrayList<>();
}
