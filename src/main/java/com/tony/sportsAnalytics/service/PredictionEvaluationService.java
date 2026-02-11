package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import org.springframework.stereotype.Service;

@Service
public class PredictionEvaluationService {

    /**
     * Évalue la qualité d'une prédiction une fois le match terminé.
     */
    public void evaluatePrediction(MatchAnalysis match) {
        if (match.getHomeScore() == null || match.getPrediction() == null) return;

        PredictionResult pred = match.getPrediction();
        int homeGoals = match.getHomeScore();
        int awayGoals = match.getAwayScore();

        // 1. Déterminer le résultat réel (1, N, 2)
        boolean homeWin = homeGoals > awayGoals;
        boolean draw = homeGoals == awayGoals;
        boolean awayWin = awayGoals > homeGoals;

        // 2. Calculer le Brier Score
        // Formule : Somme des (ProbaEstimée - RésultatRéel)^2
        // RésultatRéel vaut 1.0 si c'est arrivé, 0.0 sinon.
        // On divise les probas par 100 car stockées en % (ex: 45.0)

        double pHome = pred.getHomeWinProbability() / 100.0;
        double pDraw = pred.getDrawProbability() / 100.0;
        double pAway = pred.getAwayWinProbability() / 100.0;

        double brier = Math.pow(pHome - (homeWin ? 1.0 : 0.0), 2) +
                Math.pow(pDraw - (draw ? 1.0 : 0.0), 2) +
                Math.pow(pAway - (awayWin ? 1.0 : 0.0), 2);

        // Brier Score varie de 0 (Parfait) à 2 (Tout faux). On le stocke.
        pred.setBrierScore(Math.round(brier * 1000.0) / 1000.0);

        // 3. Est-ce "Correct" ? (Si le résultat réel avait la plus haute proba OU > 40%)
        double maxProb = Math.max(pHome, Math.max(pDraw, pAway));
        boolean isCorrect = false;

        if (homeWin && (pHome == maxProb || pHome >= 0.40)) isCorrect = true;
        else if (draw && (pDraw == maxProb || pDraw >= 0.30)) isCorrect = true; // Seuil plus bas pour le nul
        else if (awayWin && (pAway == maxProb || pAway >= 0.40)) isCorrect = true;

        pred.setPredictionCorrect(isCorrect);

        // (Optionnel) Calcul RPS ici si besoin de plus de précision
    }
}
