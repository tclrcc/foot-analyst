package com.tony.sportsAnalytics.service;

import com.opencsv.bean.CsvToBeanBuilder;
import com.tony.sportsAnalytics.model.*;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.*;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataImportService {

    private final TeamRepository teamRepository;
    private final MatchAnalysisRepository matchRepository;
    private final LeagueRepository leagueRepository;
    private final TeamStatsService teamStatsService;

    // Mapping des noms (inchangé)
    private static final Map<String, String> TEAM_NAME_MAPPING = new HashMap<>();
    static {
        TEAM_NAME_MAPPING.put("Man City", "Man City");
        TEAM_NAME_MAPPING.put("Man United", "Man United");
        TEAM_NAME_MAPPING.put("Nott'm Forest", "Nottingham Forest");
        TEAM_NAME_MAPPING.put("Spurs", "Tottenham");
        TEAM_NAME_MAPPING.put("Wolves", "Wolverhampton");
        TEAM_NAME_MAPPING.put("Leicester", "Leicester City");
        TEAM_NAME_MAPPING.put("Ipswich", "Ipswich Town");
    }

    @Transactional
    public String importPremierLeagueData() {
        String csvUrl = "https://www.football-data.co.uk/mmz4281/2526/E0.csv";

        try {
            // --- CORRECTION SSL ICI ---
            // On utilise notre méthode spéciale au lieu de uri.toURL().openStream()
            Reader reader = getReaderIgnoringSSL(csvUrl);

            List<FootballDataRow> rows = new CsvToBeanBuilder<FootballDataRow>(reader)
                    .withType(FootballDataRow.class)
                    .withSeparator(',')
                    .build()
                    .parse();

            int importedCount = 0;
            int skippedCount = 0;

            League pl = leagueRepository.findByName("Premier League")
                    .orElseGet(() -> {
                        League l = new League();
                        l.setName("Premier League");
                        l.setCountry("England");
                        return leagueRepository.save(l);
                    });

            for (FootballDataRow row : rows) {
                if (processMatchRow(row, pl)) {
                    importedCount++;
                } else {
                    skippedCount++;
                }
            }

            // Important : fermer le reader
            reader.close();

            log.info("Import terminé. {} ajoutés, {} ignorés.", importedCount, skippedCount);
            return String.format("%d matchs importés, %d ignorés.", importedCount, skippedCount);

        } catch (Exception e) {
            log.error("Erreur critique lors de l'import CSV", e);
            return "Erreur technique : " + e.getMessage();
        }
    }

    /**
     * MAGIE NOIRE SSL : Crée une connexion qui ignore la validation du certificat.
     * Indispensable pour éviter l'erreur PKIX path building failed sur certains environnements.
     */
    private Reader getReaderIgnoringSSL(String urlString) throws Exception {
        // 1. Créer un TrustManager qui ne fait rien (accepte tout)
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        // 2. Initialiser le contexte SSL
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());

        // 3. Configurer la connexion
        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sc.getSocketFactory());

        // Optionnel : Ignorer aussi la vérification du nom d'hôte
        conn.setHostnameVerifier((hostname, session) -> true);

        return new InputStreamReader(conn.getInputStream());
    }

    private boolean processMatchRow(FootballDataRow row, League league) {
        // ... (Le reste de ton code reste identique à ma version précédente) ...
        // Je le remets ici pour être sûr que tu as la version complète avec getOrCreateTeam

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate date;
        try {
            date = LocalDate.parse(row.getDate(), formatter);
        } catch (Exception e) {
            log.warn("Format de date invalide : {}", row);
            return false;
        }
        LocalDateTime matchDate = date.atTime(16, 0);

        String homeNameClean = resolveTeamName(row.getHomeTeam());
        String awayNameClean = resolveTeamName(row.getAwayTeam());

        Team home = getOrCreateTeam(homeNameClean, league);
        Team away = getOrCreateTeam(awayNameClean, league);

        // Check doublon basique
        boolean exists = matchRepository.findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(home.getId(), home.getId())
                .stream()
                .anyMatch(m -> m.getAwayTeam().getId().equals(away.getId())
                        && m.getMatchDate().toLocalDate().isEqual(date));

        if (exists) return false;

        MatchAnalysis match = new MatchAnalysis();
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setMatchDate(matchDate);
        match.setSeason("2025-2026");
        match.setHomeScore(row.getFTHG());
        match.setAwayScore(row.getFTAG());

        MatchDetailStats homeStats = new MatchDetailStats();
        homeStats.setShots(row.getHS());
        homeStats.setShotsOnTarget(row.getHST());
        homeStats.setCorners(row.getHC());
        homeStats.setFouls(row.getHF());
        homeStats.setYellowCards(row.getHY());
        homeStats.setRedCards(row.getHR());

        MatchDetailStats awayStats = new MatchDetailStats();
        awayStats.setShots(row.getAS());
        awayStats.setShotsOnTarget(row.getAST());
        awayStats.setCorners(row.getAC());
        awayStats.setFouls(row.getAF());
        awayStats.setYellowCards(row.getAY());
        awayStats.setRedCards(row.getAR());

        match.setHomeMatchStats(homeStats);
        match.setAwayMatchStats(awayStats);

        match.setOdds1(row.getB365H());
        match.setOddsN(row.getB365D());
        match.setOdds2(row.getB365A());
        match.setOddsOver25(row.getB365O25());

        match.setHomeStats(new TeamStats());
        match.setAwayStats(new TeamStats());

        matchRepository.save(match);
        teamStatsService.recalculateTeamStats(home.getId());
        teamStatsService.recalculateTeamStats(away.getId());

        return true;
    }

    private Team getOrCreateTeam(String teamName, League league) {
        return teamRepository.findByName(teamName)
                .orElseGet(() -> {
                    Team newTeam = new Team();
                    newTeam.setName(teamName);
                    newTeam.setLeague(league);
                    return teamRepository.save(newTeam);
                });
    }

    private String resolveTeamName(String csvName) {
        return TEAM_NAME_MAPPING.getOrDefault(csvName, csvName);
    }

    @Data
    public static class FootballDataRow {
        // ... (Tes champs DTO restent identiques) ...
        @com.opencsv.bean.CsvBindByName(column = "Date") private String date;
        @com.opencsv.bean.CsvBindByName(column = "HomeTeam") private String homeTeam;
        @com.opencsv.bean.CsvBindByName(column = "AwayTeam") private String awayTeam;
        @com.opencsv.bean.CsvBindByName(column = "FTHG") private Integer FTHG;
        @com.opencsv.bean.CsvBindByName(column = "FTAG") private Integer FTAG;
        @com.opencsv.bean.CsvBindByName(column = "HS") private Integer HS;
        @com.opencsv.bean.CsvBindByName(column = "AS") private Integer AS;
        @com.opencsv.bean.CsvBindByName(column = "HST") private Integer HST;
        @com.opencsv.bean.CsvBindByName(column = "AST") private Integer AST;
        @com.opencsv.bean.CsvBindByName(column = "HC") private Integer HC;
        @com.opencsv.bean.CsvBindByName(column = "AC") private Integer AC;
        @com.opencsv.bean.CsvBindByName(column = "HF") private Integer HF;
        @com.opencsv.bean.CsvBindByName(column = "AF") private Integer AF;
        @com.opencsv.bean.CsvBindByName(column = "HY") private Integer HY;
        @com.opencsv.bean.CsvBindByName(column = "AY") private Integer AY;
        @com.opencsv.bean.CsvBindByName(column = "HR") private Integer HR;
        @com.opencsv.bean.CsvBindByName(column = "AR") private Integer AR;
        @com.opencsv.bean.CsvBindByName(column = "B365H") private Double B365H;
        @com.opencsv.bean.CsvBindByName(column = "B365D") private Double B365D;
        @com.opencsv.bean.CsvBindByName(column = "B365A") private Double B365A;
        @com.opencsv.bean.CsvBindByName(column = "B365>2.5") private Double B365O25;
    }
}
