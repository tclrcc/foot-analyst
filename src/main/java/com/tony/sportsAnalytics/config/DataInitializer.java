package com.tony.sportsAnalytics.config;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;

    @Override
    public void run(String... args) throws Exception {
        // On ne remplit que si la base est vide
        if (leagueRepository.count() == 0) {
            System.out.println("🌱 Initialisation des données de référence...");

            // 1. Création Ligue 1
            League ligue1 = leagueRepository.save(new League("Ligue 1"));
            teamRepository.saveAll(Arrays.asList(
                    new Team("PSG", ligue1),
                    new Team("Lens", ligue1),
                    new Team("Lyon", ligue1),
                    new Team("Marseille", ligue1),
                    new Team("Lille", ligue1),
                    new Team("Rennes", ligue1),
                    new Team("Strasbourg", ligue1),
                    new Team("Toulouse", ligue1),
                    new Team("Angers", ligue1),
                    new Team("Monaco", ligue1),
                    new Team("Lorient", ligue1),
                    new Team("Brest", ligue1),
                    new Team("Le Havre", ligue1),
                    new Team("Nice", ligue1),
                    new Team("Paris FC", ligue1),
                    new Team("Auxerre", ligue1),
                    new Team("Nantes", ligue1),
                    new Team("Metz", ligue1)
            ));

            // 2. Création Premier League
            League pl = leagueRepository.save(new League("Premier League"));
            teamRepository.saveAll(Arrays.asList(
                    new Team("Arsenal", pl),
                    new Team("Manchester City", pl),
                    new Team("Aston Villa", pl),
                    new Team("Manchester United", pl),
                    new Team("Chelsea", pl),
                    new Team("Liverpool", pl),
                    new Team("Brentford", pl),
                    new Team("Everton", pl),
                    new Team("Sunderland", pl),
                    new Team("Fulham", pl),
                    new Team("Bournemouth", pl),
                    new Team("Newcastle", pl),
                    new Team("Crystal Palace", pl),
                    new Team("Brighton", pl),
                    new Team("Tottenham", pl),
                    new Team("Leeds", pl),
                    new Team("Nottingham", pl),
                    new Team("West Ham", pl),
                    new Team("Burnley", pl),
                    new Team("Wolves", pl)
            ));

            System.out.println("✅ Données initialisées !");
        }
    }
}
