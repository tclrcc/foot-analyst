package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.dto.DashboardStats;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MatchAnalysisRepository repository;

    public DashboardStats getKpi() {
        List<MatchAnalysis> all = repository.findAll();

        // Filtrer les matchs terminés (ceux qui ont un score réel)
        List<MatchAnalysis> finished = all.stream()
                .filter(m -> m.getHomeScore() != null && m.getAwayScore() != null)
                .toList();

        long correct = 0;
        long confidentTotal = 0;
        int totalMatches = 0;
        long confidentCorrect = 0;

        for (MatchAnalysis m : finished) {
            totalMatches++;
            if (m.getPrediction() == null) {
                continue; // On passe au match suivant si pas de prédiction
            }

            int actualResult = Integer.compare(m.getHomeScore(), m.getAwayScore()); // 1 (Home), 0 (Draw), -1 (Away)

            // Prédiction de l'algo (celui avec la plus haute proba)
            Double pHome = m.getPrediction().getHomeWinProbability();
            Double pDraw = m.getPrediction().getDrawProbability();
            Double pAway = m.getPrediction().getAwayWinProbability();

            // Sécurité supplémentaire sur les champs internes (au cas où ils seraient null aussi)
            if (pHome == null || pAway == null || pDraw == null) continue;

            int predictedResult = 0;
            if (pHome > pDraw && pHome > pAway) predictedResult = 1;
            else if (pAway > pHome && pAway > pDraw) predictedResult = -1;

            // Vérification
            boolean isCorrect = (actualResult == predictedResult);
            if (isCorrect) correct++;

            // Check Confiance (> 60%)
            double maxProb = Math.max(pHome, Math.max(pDraw, pAway));
            if (maxProb > 60.0) {
                confidentTotal++;
                if (isCorrect) confidentCorrect++;
            }
        }

        double accGlobal = finished.isEmpty() ? 0.0 : (double) correct / finished.size() * 100;
        double accConfident = confidentTotal == 0 ? 0.0 : (double) confidentCorrect / confidentTotal * 100;

        return new DashboardStats(all.size(), finished.size(), round(accGlobal), round(accConfident));
    }

    private double round(double val) { return Math.round(val * 10.0) / 10.0; }
}
