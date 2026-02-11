package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor // Remplacement de @Data
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "league_id"})
})
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "league_id")
    private League league;

    @Embedded
    private TeamStats currentStats;

    @Column(nullable = false, columnDefinition = "integer default 1500")
    private Integer eloRating = 1500;

    private String stadiumCity;
    private Double latitude;
    private Double longitude;

    public Team(String name, League league) {
        this.name = name;
        this.league = league;
        this.eloRating = 1500;
    }

    // HashCode compatible JPA (évite les bugs quand l'ID change après save)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Team)) return false;
        return id != null && id.equals(((Team) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
