package com.tony.sportsAnalytics.config;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Optionnel : pour des logs propres
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final LeagueRepository leagueRepository;

    @Override
    public void run(String... args) {
        // On vérifie si la base contient déjà des ligues pour ne pas créer de doublons
        if (leagueRepository.count() == 0) {
            log.info("🌱 Base de données vide : Initialisation des 5 Grands Championnats...");

            List<League> big5 = Arrays.asList(
                    // 1. Premier League (Angleterre)
                    // Note: 'gb-eng' ou 'gb' selon ta librairie d'icônes, ici 'gb-eng' est souvent plus précis
                    new League("Premier League", "England", "gb-eng"),

                    // 2. Ligue 1 (France)
                    new League("Ligue 1", "France", "fr"),

                    // 3. La Liga (Espagne)
                    new League("La Liga", "Spain", "es"),

                    // 4. Serie A (Italie)
                    new League("Serie A", "Italy", "it"),

                    // 5. Bundesliga (Allemagne)
                    new League("Bundesliga", "Germany", "de")
            );

            leagueRepository.saveAll(big5);
            log.info("✅ Les 5 ligues ont été insérées avec succès !");
        } else {
            log.info("⚡ La base de données contient déjà des ligues. Initialisation ignorée.");
        }
    }
}
