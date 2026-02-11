package com.tony.sportsAnalytics.config;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.service.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final LeagueRepository leagueRepository;
    private final MatchAnalysisRepository matchRepository;
    private final DataImportService dataImportService;

    @Override
    public void run(String... args) {
        log.info("🏁 Démarrage du DataInitializer...");

        // 1. Initialisation des Ligues (Référentiel)
        if (leagueRepository.count() == 0) {
            log.info("🌱 Base vide : Création des 5 Ligues...");
            List<League> big5 = Arrays.asList(
                    new League("Premier League", "England", "gb-eng"),
                    new League("Ligue 1", "France", "fr"),
                    new League("La Liga", "Spain", "es"),
                    new League("Serie A", "Italy", "it"),
                    new League("Bundesliga", "Germany", "de")
            );
            leagueRepository.saveAll(big5);
            log.info("✅ Ligues créées.");
        }

        // 2. Initialisation de l'Historique des Matchs (Lourd)
        // On ne le fait QUE s'il n'y a aucun match en base.
        if (matchRepository.count() == 0) {
            log.info("📜 Aucun match détecté. Lancement de l'import historique MASSIF (Saisons 2021-2025)...");
            log.info("☕ Prenez un café, cela peut prendre 1 à 2 minutes.");

            try {
                String report = dataImportService.importFullHistory();
                log.info(report);
            } catch (Exception e) {
                log.error("❌ Erreur critique lors de l'initialisation des données", e);
            }
        } else {
            log.info("⚡ Les matchs sont déjà présents en base. Import historique ignoré.");
        }

        // 3. (Optionnel) Mise à jour des futurs matchs au démarrage
        // Pour être sûr d'avoir les cotes du jour même après un restart
        log.info("🔮 Vérification des matchs à venir...");
        dataImportService.importUpcomingFixtures();

        log.info("🚀 Application prête !");
    }
}
