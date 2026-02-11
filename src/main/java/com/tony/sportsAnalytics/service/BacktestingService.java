package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestingService {
    private final MatchAnalysisRepository matchRepository;
    private final PredictionEngineService predictionEngine;

    public void runBacktest(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59);

        // 1. On récupère les matchs de la période de test
        List<MatchAnalysis> testMatches = matchRepository.findByMatchDateBetweenOrderByMatchDateAsc(start, end);

        double totalBrierScore = 0;
        int count = 0;

        for (MatchAnalysis m : testMatches) {
            if (m.getHomeScore() == null) continue;

            // 2. Isolation des données : On ne prend que le passé par rapport au match 'm'
            LocalDateTime limit = m.getMatchDate();

            // On récupère l'historique complet disponible à l'instant T du match
            List<MatchAnalysis> h2h = matchRepository.findHeadToHeadGlobal(m.getHomeTeam().getId(), m.getAwayTeam().getId())
                    .stream().filter(match -> match.getMatchDate().isBefore(limit)).collect(Collectors.toList());

            List<MatchAnalysis> homeHist = matchRepository.findLastMatchesByTeam(m.getHomeTeam().getId())
                    .stream().filter(match -> match.getMatchDate().isBefore(limit)).collect(Collectors.toList());

            List<MatchAnalysis> awayHist = matchRepository.findLastMatchesByTeam(m.getAwayTeam().getId())
                    .stream().filter(match -> match.getMatchDate().isBefore(limit)).collect(Collectors.toList());

            double leagueAvg = (m.getHomeTeam().getLeague() != null) ? m.getHomeTeam().getLeague().getAverageGoalsPerMatch() : 2.5;

            // 3. Re-simulation
            PredictionResult pred = predictionEngine.calculateMatchPrediction(m, h2h, homeHist, awayHist, leagueAvg);

            // 4. Calcul de l'erreur
            double brier = calculateMatchBrierScore(pred, m.getHomeScore(), m.getAwayScore());
            totalBrierScore += brier;
            count++;
        }

        if (count > 0) {
            double finalScore = totalBrierScore / count;
            log.info("📊 BACKTEST TERMINE sur {} matchs", count);
            log.info("🎯 Brier Score moyen : {}", String.format("%.4f", finalScore));
            log.info("💡 (Note: Plus il est proche de 0, plus l'algo est précis. > 0.66 = Hasard pur)");
        }
    }

    private double calculateMatchBrierScore(PredictionResult p, int gh, int ga) {
        double oH = (gh > ga) ? 1.0 : 0.0;
        double oD = (gh == ga) ? 1.0 : 0.0;
        double oA = (gh < ga) ? 1.0 : 0.0;

        return (Math.pow((p.getHomeWinProbability()/100.0) - oH, 2) +
                Math.pow((p.getDrawProbability()/100.0) - oD, 2) +
                Math.pow((p.getAwayWinProbability()/100.0) - oA, 2)) / 3.0;
    }
}
