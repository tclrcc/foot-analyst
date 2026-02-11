package com.tony.sportsAnalytics.job;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import com.tony.sportsAnalytics.service.DataImportService;
import com.tony.sportsAnalytics.service.ParameterEstimationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyUpdateJob {

    private final DataImportService dataImportService;
    private final ParameterEstimationService estimationService;
    private final MatchAnalysisRepository matchRepository;
    private final TeamRepository teamRepository;

    /**
     * JOB 1 : Mise à jour des RÉSULTATS (Matchs joués la veille)
     * Fréquence : Tous les jours à 09:00 du matin (heure serveur)
     * Pourquoi 9h ? Pour laisser le temps au mainteneur du site anglais de mettre à jour ses CSV.
     */
    @Scheduled(cron = "0 0 9 * * *") // Secondes Minutes Heures Jours Mois JoursSemaine
    public void updateResults() {
        log.info("⏰ [CRON] Démarrage automatique : Mise à jour des résultats...");
        try {
            // On lance l'import global sans forcer (false).
            // Grâce à notre "Smart Update", cela va :
            // 1. Ignorer les matchs déjà complets.
            // 2. Mettre à jour les matchs qui étaient "À venir" et qui ont maintenant un score.

            // Note : Pour optimiser, on pourrait n'appeler que importLeagueData pour chaque ligue,
            // mais importAllLeagues est plus simple et robuste.
            for (String leagueCode : dataImportService.getAvailableLeagues()) {
                String report = dataImportService.importLeagueData(leagueCode, false);
                log.info("   -> {}", report);
            }
            log.info("✅ [CRON] Résultats mis à jour avec succès.");
        } catch (Exception e) {
            log.error("❌ [CRON] Echec de la mise à jour des résultats", e);
        }
    }

    /**
     * JOB 2 : Import des MATCHS À VENIR (Cotes & Calendrier)
     * Fréquence : Tous les jours à 09:30 (juste après les résultats)
     * Et aussi le Vendredi à 18:00 pour les dernières cotes du week-end.
     */
    @Scheduled(cron = "0 30 9 * * *")
    @Scheduled(cron = "0 0 18 * * FRI")
    public void updateFixtures() {
        log.info("⏰ [CRON] Démarrage automatique : Import des futurs matchs (Fixtures)...");
        try {
            String report = dataImportService.importUpcomingFixtures();
            log.info("   -> {}", report);
            log.info("✅ [CRON] Fixtures importées.");
        } catch (Exception e) {
            log.error("❌ [CRON] Echec import fixtures", e);
        }
    }

    @Scheduled(cron = "0 45 9 * * *") // 15 min après l'import des résultats
    public void recalibrateModel() {
        log.info("📊 Recalibrage du modèle Dixon-Coles...");

        // On récupère les 100 derniers matchs pour la précision (ou toute la saison)
        List<MatchAnalysis> historicalMatches = matchRepository.findAll();
        List<Team> allTeams = teamRepository.findAll();

        // Déclenche l'optimisation mathématique (MLE)
        estimationService.estimateParameters(historicalMatches, allTeams);
        log.info("✅ Paramètres Alpha/Beta mis à jour pour toutes les équipes.");
    }
}
