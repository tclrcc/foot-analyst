package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "league_id")
    private League league;

    // --- NOUVEAU V7 : Stats Actuelles (Classement Live) ---
    @Embedded
    private TeamStats currentStats;

    public Team(String name, League league) {
        this.name = name;
        this.league = league;
    }
}
