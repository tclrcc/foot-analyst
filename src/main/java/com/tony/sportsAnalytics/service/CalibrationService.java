package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.League;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalibrationService {

    /**
     * Applique la formule de Platt Scaling : 1 / (1 + exp(A * p + B))
     */
    public double calibrate(double rawProb, double a, double b) {
        double p = rawProb / 100.0; // Normalisation 0-1
        double calibrated = 1.0 / (1.0 + Math.exp(a * p + b));
        return calibrated * 100.0;
    }

    /**
     * Note : Pour optimiser A et B, on utilise généralement une descente de gradient
     * basée sur les CalibrationData collectées lors du backtest.
     */
    public void updateLeagueCalibration(League league, List<BacktestingService.CalibrationData> data) {
        // Logique simplifiée : Ajustement progressif basé sur l'erreur moyenne
        double avgPredicted = data.stream().mapToDouble(BacktestingService.CalibrationData::getPredictedProb).average().orElse(0.5);
        double avgActual = data.stream().mapToDouble(BacktestingService.CalibrationData::getActualOutcome).average().orElse(0.5);

        if (avgPredicted > avgActual) {
            league.setCalibrationB(league.getCalibrationB() + 0.1); // On réduit la probabilité globale
            log.info("Ajustement de calibration pour {}: Modèle trop confiant, augmentation du frein B", league.getName());
        }
    }
}
