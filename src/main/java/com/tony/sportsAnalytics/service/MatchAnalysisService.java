package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.*;
import com.tony.sportsAnalytics.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchAnalysisService {

    private final MatchAnalysisRepository matchRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final PredictionEngineService predictionEngine;

    @Transactional
    public MatchAnalysis analyzeAndSave(MatchAnalysis matchAnalysis) {
        // 1. Gestion Intelligente des Données de Référence (League / Team)
        // On récupère les noms saisis dans le JSON (champs @Transient)
        String leagueName = matchAnalysis.getLeagueNameInput();
        String homeName = matchAnalysis.getHomeTeamNameInput();
        String awayName = matchAnalysis.getAwayTeamNameInput();

        // Étape A : Gérer la Ligue
        // On cherche si elle existe, sinon on la crée
        League league = leagueRepository.findByName(leagueName)
                .orElseGet(() -> leagueRepository.save(new League(leagueName)));

        // Étape B : Gérer les Équipes
        // Idem : Find or Create, et on les associe à la Ligue trouvée
        Team homeTeam = teamRepository.findByName(homeName)
                .orElseGet(() -> teamRepository.save(new Team(homeName, league)));

        Team awayTeam = teamRepository.findByName(awayName)
                .orElseGet(() -> teamRepository.save(new Team(awayName, league)));

        // On attache les vrais objets Team au Match
        matchAnalysis.setHomeTeam(homeTeam);
        matchAnalysis.setAwayTeam(awayTeam);

        // 2. Appel de l'algo de prédiction (inchangé)
        PredictionResult prediction = predictionEngine.calculateMatchPrediction(matchAnalysis);
        matchAnalysis.setPrediction(prediction);

        // 3. Sauvegarde
        return matchRepository.save(matchAnalysis);
    }
}
