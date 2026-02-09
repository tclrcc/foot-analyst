package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "match_analysis")
@Data
public class MatchAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @Column(nullable = false)
    private LocalDateTime matchDate;

    @Column(length = 9) // Ex: "2025-2026"
    private String season;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "rank", column = @Column(name = "home_rank")),
            @AttributeOverride(name = "points", column = @Column(name = "home_points")),
            @AttributeOverride(name = "matchesPlayed", column = @Column(name = "home_mj_total")),
            @AttributeOverride(name = "matchesPlayedHome", column = @Column(name = "home_mj_home")),
            @AttributeOverride(name = "matchesPlayedAway", column = @Column(name = "home_mj_away")),
            @AttributeOverride(name = "goalsFor", column = @Column(name = "home_goals_for")),
            @AttributeOverride(name = "goalsAgainst", column = @Column(name = "home_goals_against")),
            @AttributeOverride(name = "xG", column = @Column(name = "home_xg")),
            @AttributeOverride(name = "last5MatchesPoints", column = @Column(name = "home_form_l5")),

            // --- AJOUTS CORRECTIFS V7 ---
            @AttributeOverride(name = "goalsForLast5", column = @Column(name = "home_goals_for_l5")),
            @AttributeOverride(name = "goalsAgainstLast5", column = @Column(name = "home_goals_against_l5")),
            // ----------------------------

            @AttributeOverride(name = "venuePoints", column = @Column(name = "home_venue_points")),
            @AttributeOverride(name = "venueMatches", column = @Column(name = "home_venue_matches"))
    })
    @Valid
    private TeamStats homeStats;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "rank", column = @Column(name = "away_rank")),
            @AttributeOverride(name = "points", column = @Column(name = "away_points")),
            @AttributeOverride(name = "matchesPlayed", column = @Column(name = "away_mj_total")),
            @AttributeOverride(name = "matchesPlayedHome", column = @Column(name = "away_mj_home")),
            @AttributeOverride(name = "matchesPlayedAway", column = @Column(name = "away_mj_away")),
            @AttributeOverride(name = "goalsFor", column = @Column(name = "away_goals_for")),
            @AttributeOverride(name = "goalsAgainst", column = @Column(name = "away_goals_against")),
            @AttributeOverride(name = "xG", column = @Column(name = "away_xg")),
            @AttributeOverride(name = "last5MatchesPoints", column = @Column(name = "away_form_l5")),

            // --- AJOUTS CORRECTIFS V7 ---
            @AttributeOverride(name = "goalsForLast5", column = @Column(name = "away_goals_for_l5")),
            @AttributeOverride(name = "goalsAgainstLast5", column = @Column(name = "away_goals_against_l5")),
            // ----------------------------

            @AttributeOverride(name = "venuePoints", column = @Column(name = "away_venue_points")),
            @AttributeOverride(name = "venueMatches", column = @Column(name = "away_venue_matches"))
    })
    @Valid
    private TeamStats awayStats;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "xG", column = @Column(name = "home_stat_xg")),
            @AttributeOverride(name = "xGOT", column = @Column(name = "home_stat_xgot")),
            @AttributeOverride(name = "shots", column = @Column(name = "home_stat_shots")),
            @AttributeOverride(name = "shotsOnTarget", column = @Column(name = "home_stat_sot")),
            @AttributeOverride(name = "bigChances", column = @Column(name = "home_stat_big_chances")),
            @AttributeOverride(name = "possession", column = @Column(name = "home_stat_possession")),
            @AttributeOverride(name = "passesTotal", column = @Column(name = "home_stat_passes_total")),
            @AttributeOverride(name = "passesCompleted", column = @Column(name = "home_stat_passes_ok")),
            @AttributeOverride(name = "crosses", column = @Column(name = "home_stat_crosses")),
            @AttributeOverride(name = "corners", column = @Column(name = "home_stat_corners")),
            @AttributeOverride(name = "fouls", column = @Column(name = "home_stat_fouls")),
            @AttributeOverride(name = "yellowCards", column = @Column(name = "home_stat_yc")),
            @AttributeOverride(name = "redCards", column = @Column(name = "home_stat_rc"))
    })
    private MatchDetailStats homeMatchStats;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "xG", column = @Column(name = "away_stat_xg")),
            @AttributeOverride(name = "xGOT", column = @Column(name = "away_stat_xgot")),
            @AttributeOverride(name = "shots", column = @Column(name = "away_stat_shots")),
            @AttributeOverride(name = "shotsOnTarget", column = @Column(name = "away_stat_sot")),
            @AttributeOverride(name = "bigChances", column = @Column(name = "away_stat_big_chances")),
            @AttributeOverride(name = "possession", column = @Column(name = "away_stat_possession")),
            @AttributeOverride(name = "passesTotal", column = @Column(name = "away_stat_passes_total")),
            @AttributeOverride(name = "passesCompleted", column = @Column(name = "away_stat_passes_ok")),
            @AttributeOverride(name = "crosses", column = @Column(name = "away_stat_crosses")),
            @AttributeOverride(name = "corners", column = @Column(name = "away_stat_corners")),
            @AttributeOverride(name = "fouls", column = @Column(name = "away_stat_fouls")),
            @AttributeOverride(name = "yellowCards", column = @Column(name = "away_stat_yc")),
            @AttributeOverride(name = "redCards", column = @Column(name = "away_stat_rc"))
    })
    private MatchDetailStats awayMatchStats;

    // --- NOUVEAU : Cotes Bookmakers (Pour calcul Value / Kelly) ---
    private Double odds1;
    private Double oddsN;
    private Double odds2;

    // --- V15 : COTES MARCHÉS SECONDAIRES ---
    @Column(name = "odds_over_1_5")
    private Double oddsOver15;

    @Column(name = "odds_over_2_5")
    private Double oddsOver25;

    @Column(name = "odds_btts_yes")
    private Double oddsBTTSYes;

    // --- NOUVEAU : Facteurs Contextuels (Booleans) ---
    private boolean homeKeyPlayerMissing; // Absence joueur clé Dom
    private boolean awayKeyPlayerMissing; // Absence joueur clé Ext
    private boolean homeTired;            // Fatigue Dom
    private boolean awayNewCoach;         // Choc psycho Ext

    private Integer homeScore;
    private Integer awayScore;

    // Pourra servir plus tard pour stocker ton analyse perso
    @Column(columnDefinition = "TEXT")
    private String myNotes;

    @Embedded
    private PredictionResult prediction;

    @Transient
    private String leagueNameInput;
    @Transient
    private String homeTeamNameInput;
    @Transient
    private String awayTeamNameInput;
}
