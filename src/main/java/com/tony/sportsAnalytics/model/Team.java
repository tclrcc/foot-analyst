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
    private String name; // Ex: "PSG"

    @ManyToOne // Si on supprime une équipe, on garde la ligue
    @JoinColumn(name = "league_id")
    private League league;

    public Team(String name, League league) {
        this.name = name;
        this.league = league;
    }
}
