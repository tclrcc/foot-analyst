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

    public League(String name) {
        this.name = name;
    }
}
