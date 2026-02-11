package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String position; // G, D, M, F

    @ManyToOne
    private Team currentTeam;

    // Stats cumulées (pour éviter de tout recalculer à chaque fois)
    private Integer seasonGoals = 0;
    private Integer seasonAssists = 0;
    private Double averageRating; // Note moyenne
}
