package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

        List<MatchAnalysis> testMatches = matchRepository.findByMatchDateBetweenOrderByMatchDateAsc(start, end);

        double totalBrierScore = 0;
        double totalLogLoss = 0;
        int count = 0;

        // Liste pour stocker chaque prédiction individuelle pour l'analyse de calibration
        List<CalibrationData> calibrationList = new ArrayList<>();

        for (MatchAnalysis m : testMatches) {
            // On ignore les matchs non joués pour le calcul de précision
            if (m.getHomeScore() == null) continue;

            LocalDateTime limit = m.getMatchDate();

            // Isolation temporelle pour éviter le Data Leakage
            List<MatchAnalysis> h2h = matchRepository.findHeadToHeadGlobal(m.getHomeTeam().getId(), m.getAwayTeam().getId())
                    .stream().filter(match -> match.getMatchDate().isBefore(limit)).collect(Collectors.toList());

            List<MatchAnalysis> homeHist = matchRepository.findLastMatchesByTeam(m.getHomeTeam().getId(), m.getMatchDate())
                    .stream().filter(match -> match.getMatchDate().isBefore(limit)).collect(Collectors.toList());

            List<MatchAnalysis> awayHist = matchRepository.findLastMatchesByTeam(m.getAwayTeam().getId(), m.getMatchDate())
                    .stream().filter(match -> match.getMatchDate().isBefore(limit)).collect(Collectors.toList());

            double leagueAvg = (m.getHomeTeam().getLeague() != null) ? m.getHomeTeam().getLeague().getAverageGoalsPerMatch() : 2.5;

            // Re-simulation avec le moteur actuel
            PredictionResult pred = predictionEngine.calculateMatchPrediction(m, h2h, homeHist, awayHist, leagueAvg);

            // --- CORRECTION MAJEURE : On met à jour l'objet pour la simulation financière suivante ---
            m.setPrediction(pred);

            // Calcul des scores d'erreur
            totalBrierScore += calculateMatchBrierScore(pred, m.getHomeScore(), m.getAwayScore());
            totalLogLoss += calculateMatchLogLoss(pred, m.getHomeScore(), m.getAwayScore());

            // Collecte des données de calibration
            calibrationList.add(new CalibrationData(pred.getHomeWinProbability() / 100.0, m.getHomeScore() > m.getAwayScore() ? 1.0 : 0.0));
            calibrationList.add(new CalibrationData(pred.getDrawProbability() / 100.0, m.getHomeScore().equals(m.getAwayScore()) ? 1.0 : 0.0));
            calibrationList.add(new CalibrationData(pred.getAwayWinProbability() / 100.0, m.getHomeScore() < m.getAwayScore() ? 1.0 : 0.0));

            count++;
        }

        if (count > 0) {
            log.info("📊 --- RÉSULTATS DU BACKTEST ---");
            log.info("🏟️  Matchs analysés : {}", count);
            log.info("🎯 Brier Score moyen : {}", String.format("%.4f", totalBrierScore / count));
            log.info("📉 Log-Loss moyenne   : {}", String.format("%.4f", totalLogLoss / count));

            printCalibrationReport(calibrationList);
        } else {
            log.warn("Aucun match terminé trouvé sur la période demandée.");
        }
    }
    /**
     * Analyse et affiche le rapport de calibration par tranches de 10%.
     */
    private void printCalibrationReport(List<CalibrationData> data) {
        log.info("🎯 --- RAPPORT DE CALIBRATION ---");
        log.info(String.format("%-15s | %-12s | %-12s | %-8s", "Tranche Prob", "Moy. Prédite", "Fréq. Réelle", "Nb Cas"));
        log.info("-------------------------------------------------------------------");

        for (int i = 0; i < 10; i++) {
            double lower = i / 10.0;
            double upper = (i + 1) / 10.0;

            List<CalibrationData> bin = data.stream()
                    .filter(d -> d.predictedProb >= lower && d.predictedProb < upper)
                    .toList();

            if (!bin.isEmpty()) {
                double avgPredicted = bin.stream().mapToDouble(d -> d.predictedProb).average().orElse(0.0);
                double actualFreq = bin.stream().mapToDouble(d -> d.actualOutcome).average().orElse(0.0);
                log.info(String.format("[%2.0f%% - %2.0f%%]   | %-12.2f | %-12.2f | %-8d",
                        lower * 100, upper * 100, avgPredicted * 100, actualFreq * 100, bin.size()));
            }
        }
    }

    private double calculateMatchLogLoss(PredictionResult p, int gh, int ga) {
        double prob;
        if (gh > ga) prob = p.getHomeWinProbability() / 100.0;
        else if (gh == ga) prob = p.getDrawProbability() / 100.0;
        else prob = p.getAwayWinProbability() / 100.0;
        return -Math.log(Math.max(prob, 1e-15));
    }

    private double calculateMatchBrierScore(PredictionResult p, int gh, int ga) {
        double oH = (gh > ga) ? 1.0 : 0.0;
        double oD = (gh == ga) ? 1.0 : 0.0;
        double oA = (gh < ga) ? 1.0 : 0.0;

        return (Math.pow((p.getHomeWinProbability()/100.0) - oH, 2) +
                Math.pow((p.getDrawProbability()/100.0) - oD, 2) +
                Math.pow((p.getAwayWinProbability()/100.0) - oA, 2)) / 3.0;
    }

    @Data
    @AllArgsConstructor
    public static class CalibrationData {
        double predictedProb;
        double actualOutcome;
    }
}
