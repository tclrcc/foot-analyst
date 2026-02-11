package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class League {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name; // Ex: "Ligue 1", "Premier League"

    private String country; // Optionnel : "France", "England"

    // Code ISO pour le drapeau (ex: "fr", "gb", "es")
    private String countryCode;

    // --- PARAMÈTRES ALGORITHMIQUES (DYNAMIQUES) ---

    // Poids de la fusion (ex: 0.55 pour Poisson, le reste pour Elo)
    @Column(nullable = false, columnDefinition = "double precision default 0.55")
    private Double weightPoisson = 0.55;

    // Calibration Sigmoïde (A = Pente, B = Seuil)
    @Column(nullable = false, columnDefinition = "double precision default -8.5")
    private Double calibrationA = -8.5;

    @Column(nullable = false, columnDefinition = "double precision default 4.2")
    private Double calibrationB = 4.2;

    // Facteur d'ancrage au marché (0.30 = on fait confiance à 30% aux bookmakers)
    @Column(nullable = false, columnDefinition = "double precision default 0.30")
    private Double marketAnchorWeight = 0.30;

    // Paramètre Dixon-Coles (interdépendance des buts, ex: -0.13)
    @Column(nullable = false, columnDefinition = "double precision default -0.13")
    private Double rho = -0.13;

    // Avantage domicile (spécifique à la ligue, calculé par MLE)
    @Column(nullable = false, columnDefinition = "double precision default 1.15")
    private Double homeAdvantageFactor = 1.15;

    @Column(nullable = false, columnDefinition = "double precision default 1.35")
    private Double averageGoalsPerTeam = 1.35;

    // --- Stats Globales Saison ---
    private Double averageGoalsPerMatch; // Ex: 2.85
    private Double percentHomeWin;       // Ex: 45.0
    private Double percentDraw;          // Ex: 25.0
    private Double percentAwayWin;       // Ex: 30.0
    private Double percentOver2_5;       // Ex: 55.0
    private Double percentBTTS;          // Ex: 52.0

    public League(String name) {
        this.name = name;
    }

    public League(String name, String country, String countryCode) {
        this.name = name;
        this.country = country;
        this.countryCode = countryCode;
    }

    public League(String name, String country, String countryCode, Double averageGoalsPerTeam) {
        this.name = name;
        this.country = country;
        this.countryCode = countryCode;
        this.averageGoalsPerTeam = averageGoalsPerTeam;
    }
}
