package com.tony.sportsAnalytics.config;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import com.tony.sportsAnalytics.service.TeamStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final MatchAnalysisRepository matchAnalysisRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final TeamStatsService teamStatsService;

    @Override
    public void run(String... args) throws Exception {
        // On ne remplit que si la base est vide
        if (leagueRepository.count() == 0) {
            System.out.println("🌱 Initialisation V3 avec drapeaux...");

            // 1. Ligue 1 (France)
            League l1 = leagueRepository.save(new League("Ligue 1", "France", "fr"));
            teamRepository.saveAll(Arrays.asList(
                    new Team("PSG", l1), new Team("OM", l1), new Team("Lyon", l1),
                    new Team("Lens", l1), new Team("Monaco", l1), new Team("Lille", l1)
            ));

            // 2. Premier League (Angleterre - utilise 'gb-eng' ou 'gb' pour le drapeau)
            League pl = leagueRepository.save(new League("Premier League", "England", "gb"));
            teamRepository.saveAll(Arrays.asList(
                    new Team("Man City", pl), new Team("Arsenal", pl),
                    new Team("Liverpool", pl), new Team("Chelsea", pl)
            ));

            // 3. La Liga (Espagne)
            League liga = leagueRepository.save(new League("La Liga", "Spain", "es"));
            teamRepository.saveAll(Arrays.asList(
                    new Team("Real Madrid", liga), new Team("Barcelona", liga), new Team("Atletico", liga),
                    new Team("Villarreal", liga), new Team("Espanyol", liga)
            ));

            System.out.println("✅ Données V3 initialisées !");
        }

        System.out.println("🔄 Recalcul de toutes les stats d'équipes basé sur l'historique...");
        teamRepository.findAll().forEach(team -> {
            teamStatsService.recalculateTeamStats(team.getId());
        });
        System.out.println("✅ Recalcul terminé.");

        // Correction Rétroactive V13
        List<MatchAnalysis> nullSeasonMatches = matchAnalysisRepository.findAll().stream()
                .filter(m -> m.getSeason() == null)
                .toList();

        for (MatchAnalysis m : nullSeasonMatches) {
            // Logique simple : Si après Août 2025 -> 2025-2026
            if (m.getMatchDate().isAfter(LocalDateTime.of(2025, 7, 1, 0, 0))) {
                m.setSeason("2025-2026");
            } else {
                m.setSeason("2024-2025"); // Par défaut pour les vieux tests
            }
            matchAnalysisRepository.save(m);
        }
    }
}
