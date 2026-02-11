package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisOrchestrator {

    private final MatchAnalysisRepository repository;
    private final MatchAnalysisService matchService;

    /**
     * À appeler après chaque import de données ou mise à jour de paramètres.
     * Recalcule tous les matchs non joués des 7 prochains jours.
     */
    @Transactional
    public void refreshUpcomingPredictions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextWeek = now.plusDays(7);

        // On cherche les matchs futurs
        List<MatchAnalysis> upcoming = repository.findUpcomingMatches(now);

        log.info("🔄 Orchestrator: Recalcul de {} matchs à venir...", upcoming.size());

        int count = 0;
        for (MatchAnalysis match : upcoming) {
            // On ne traite que les matchs proches pour économiser les ressources
            if (match.getMatchDate().isBefore(nextWeek)) {
                matchService.recalculatePrediction(match.getId());
                count++;
            }
        }
        log.info("✅ Orchestrator: {} prédictions mises à jour avec les derniers paramètres.", count);
    }
}
