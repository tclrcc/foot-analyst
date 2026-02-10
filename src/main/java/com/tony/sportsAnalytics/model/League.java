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
