package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
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

    @Column(length = 9)
    private String season;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "rank", column = @Column(name = "home_rank")),
            @AttributeOverride(name = "rankHome", column = @Column(name = "home_rank_home")), // Ajout
            @AttributeOverride(name = "rankAway", column = @Column(name = "home_rank_away")), // Ajout
            @AttributeOverride(name = "points", column = @Column(name = "home_points")),
            @AttributeOverride(name = "pointsHome", column = @Column(name = "home_points_home")), // Ajout
            @AttributeOverride(name = "pointsAway", column = @Column(name = "home_points_away")), // Ajout
            @AttributeOverride(name = "matchesPlayed", column = @Column(name = "home_mj_total")),
            @AttributeOverride(name = "matchesPlayedHome", column = @Column(name = "home_mj_home")),
            @AttributeOverride(name = "matchesPlayedAway", column = @Column(name = "home_mj_away")),
            @AttributeOverride(name = "wins", column = @Column(name = "home_wins")), // Ajout
            @AttributeOverride(name = "draws", column = @Column(name = "home_draws")), // Ajout
            @AttributeOverride(name = "losses", column = @Column(name = "home_losses")), // Ajout
            @AttributeOverride(name = "winsHome", column = @Column(name = "home_wins_home")), // Ajout
            @AttributeOverride(name = "drawsHome", column = @Column(name = "home_draws_home")), // Ajout
            @AttributeOverride(name = "lossesHome", column = @Column(name = "home_losses_home")), // Ajout
            @AttributeOverride(name = "goalsForHome", column = @Column(name = "home_goals_for_home")), // Ajout
            @AttributeOverride(name = "goalsAgainstHome", column = @Column(name = "home_goals_against_home")), // Ajout
            @AttributeOverride(name = "winsAway", column = @Column(name = "home_wins_away")), // Ajout
            @AttributeOverride(name = "drawsAway", column = @Column(name = "home_draws_away")), // Ajout
            @AttributeOverride(name = "lossesAway", column = @Column(name = "home_losses_away")), // Ajout
            @AttributeOverride(name = "goalsForAway", column = @Column(name = "home_goals_for_away")), // Ajout
            @AttributeOverride(name = "goalsAgainstAway", column = @Column(name = "home_goals_against_away")), // Ajout
            @AttributeOverride(name = "goalsFor", column = @Column(name = "home_goals_for")),
            @AttributeOverride(name = "goalsAgainst", column = @Column(name = "home_goals_against")),
            @AttributeOverride(name = "xG", column = @Column(name = "home_xg")),
            @AttributeOverride(name = "xGA", column = @Column(name = "home_xga")),
            @AttributeOverride(name = "ppda", column = @Column(name = "home_ppda")),
            @AttributeOverride(name = "fieldTilt", column = @Column(name = "home_field_tilt")),
            @AttributeOverride(name = "deepEntries", column = @Column(name = "home_deep_entries")),
            @AttributeOverride(name = "last5MatchesPoints", column = @Column(name = "home_form_l5")),
            @AttributeOverride(name = "goalsForLast5", column = @Column(name = "home_goals_for_l5")),
            @AttributeOverride(name = "goalsAgainstLast5", column = @Column(name = "home_goals_against_l5")),
            @AttributeOverride(name = "venuePoints", column = @Column(name = "home_venue_points")),
            @AttributeOverride(name = "venueMatches", column = @Column(name = "home_venue_matches")),
            @AttributeOverride(name = "avgShots", column = @Column(name = "home_avg_shots")),
            @AttributeOverride(name = "avgShotsOnTarget", column = @Column(name = "home_avg_sot")),
            @AttributeOverride(name = "avgPossession", column = @Column(name = "home_avg_possession")),
            @AttributeOverride(name = "avgCorners", column = @Column(name = "home_avg_corners")),
            @AttributeOverride(name = "avgCrosses", column = @Column(name = "home_avg_crosses"))
    })
    @Valid
    private TeamStats homeStats;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "rank", column = @Column(name = "away_rank")),
            @AttributeOverride(name = "rankHome", column = @Column(name = "away_rank_home")), // Ajout
            @AttributeOverride(name = "rankAway", column = @Column(name = "away_rank_away")), // Ajout
            @AttributeOverride(name = "points", column = @Column(name = "away_points")),
            @AttributeOverride(name = "pointsHome", column = @Column(name = "away_points_home")), // Ajout
            @AttributeOverride(name = "pointsAway", column = @Column(name = "away_points_away")), // Ajout
            @AttributeOverride(name = "matchesPlayed", column = @Column(name = "away_mj_total")),
            @AttributeOverride(name = "matchesPlayedHome", column = @Column(name = "away_mj_home")),
            @AttributeOverride(name = "matchesPlayedAway", column = @Column(name = "away_mj_away")),
            @AttributeOverride(name = "wins", column = @Column(name = "away_wins")), // Ajout
            @AttributeOverride(name = "draws", column = @Column(name = "away_draws")), // Ajout
            @AttributeOverride(name = "losses", column = @Column(name = "away_losses")), // Ajout
            @AttributeOverride(name = "winsHome", column = @Column(name = "away_wins_home")), // Ajout
            @AttributeOverride(name = "drawsHome", column = @Column(name = "away_draws_home")), // Ajout
            @AttributeOverride(name = "lossesHome", column = @Column(name = "away_losses_home")), // Ajout
            @AttributeOverride(name = "goalsForHome", column = @Column(name = "away_goals_for_home")), // Ajout
            @AttributeOverride(name = "goalsAgainstHome", column = @Column(name = "away_goals_against_home")), // Ajout
            @AttributeOverride(name = "winsAway", column = @Column(name = "away_wins_away")), // Ajout
            @AttributeOverride(name = "drawsAway", column = @Column(name = "away_draws_away")), // Ajout
            @AttributeOverride(name = "lossesAway", column = @Column(name = "away_losses_away")), // Ajout
            @AttributeOverride(name = "goalsForAway", column = @Column(name = "away_goals_for_away")), // Ajout
            @AttributeOverride(name = "goalsAgainstAway", column = @Column(name = "away_goals_against_away")), // Ajout
            @AttributeOverride(name = "goalsFor", column = @Column(name = "away_goals_for")),
            @AttributeOverride(name = "goalsAgainst", column = @Column(name = "away_goals_against")),
            @AttributeOverride(name = "xG", column = @Column(name = "away_xg")),
            @AttributeOverride(name = "xGA", column = @Column(name = "away_xga")),
            @AttributeOverride(name = "ppda", column = @Column(name = "away_ppda")),
            @AttributeOverride(name = "fieldTilt", column = @Column(name = "away_field_tilt")),
            @AttributeOverride(name = "deepEntries", column = @Column(name = "away_deep_entries")),
            @AttributeOverride(name = "last5MatchesPoints", column = @Column(name = "away_form_l5")),
            @AttributeOverride(name = "goalsForLast5", column = @Column(name = "away_goals_for_l5")),
            @AttributeOverride(name = "goalsAgainstLast5", column = @Column(name = "away_goals_against_l5")),
            @AttributeOverride(name = "venuePoints", column = @Column(name = "away_venue_points")),
            @AttributeOverride(name = "venueMatches", column = @Column(name = "away_venue_matches")),
            @AttributeOverride(name = "avgShots", column = @Column(name = "away_avg_shots")),
            @AttributeOverride(name = "avgShotsOnTarget", column = @Column(name = "away_avg_sot")),
            @AttributeOverride(name = "avgPossession", column = @Column(name = "away_avg_possession")),
            @AttributeOverride(name = "avgCorners", column = @Column(name = "away_avg_corners")),
            @AttributeOverride(name = "avgCrosses", column = @Column(name = "away_avg_crosses"))
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

    private Double odds1;
    private Double oddsN;
    private Double odds2;

    @Column(name = "odds_over_1_5")
    private Double oddsOver15;

    @Column(name = "odds_over_2_5")
    private Double oddsOver25;

    @Column(name = "odds_btts_yes")
    private Double oddsBTTSYes;

    private String referee;
    private Integer homeScoreHT;
    private Integer awayScoreHT;
    private Double oddsUnder25;

    private boolean homeKeyPlayerMissing;
    private boolean awayKeyPlayerMissing;
    private boolean homeTired;
    private boolean awayNewCoach;

    private Double homeMissingImpactScore = 0.0;
    private Double awayMissingImpactScore = 0.0;

    private Integer homeScore;
    private Integer awayScore;

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

    // SUPPRESSION DE LA MÉTHODE applyPlayerImpact CAR LOGIQUE TRANSFÉRÉE AU SERVICE
}
