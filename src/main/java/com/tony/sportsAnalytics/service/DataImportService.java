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
import java.net.URI;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataImportService {

    private final TeamRepository teamRepository;
    private final MatchAnalysisRepository matchRepository;
    private final LeagueRepository leagueRepository;
    private final TeamStatsService teamStatsService;
    private final PredictionEngineService predictionEngineService;

    // --- CONFIGURATION BIG 5 ---
    private static final Map<String, String> LEAGUE_URLS = Map.of(
            "PL", "https://www.football-data.co.uk/mmz4281/2526/E0.csv",    // Premier League
            "L1", "https://www.football-data.co.uk/mmz4281/2526/F1.csv",    // Ligue 1
            "LIGA", "https://www.football-data.co.uk/mmz4281/2526/SP1.csv",  // La Liga
            "SERIEA", "https://www.football-data.co.uk/mmz4281/2526/I1.csv", // Serie A
            "BUNDES", "https://www.football-data.co.uk/mmz4281/2526/D1.csv"  // Bundesliga
    );

    private static final String FIXTURES_URL = "https://www.football-data.co.uk/fixtures.csv";

    private static final Map<String, String> DIV_TO_LEAGUE_CODE = Map.of(
            "E0", "PL", "F1", "L1", "SP1", "LIGA", "I1", "SERIEA", "D1", "BUNDES"
    );

    private static final String CURRENT_SEASON = "2025-2026";

    // Mapping noms CSV spécifiques -> Noms propres BDD
    private static final Map<String, String> TEAM_NAME_MAPPING = new HashMap<>();
    static {
        TEAM_NAME_MAPPING.put("Man City", "Man City");
        TEAM_NAME_MAPPING.put("Man United", "Man United");
        TEAM_NAME_MAPPING.put("Spurs", "Tottenham");
        TEAM_NAME_MAPPING.put("Paris SG", "PSG");
        TEAM_NAME_MAPPING.put("Marseille", "OM");
        TEAM_NAME_MAPPING.put("St Etienne", "Saint-Etienne");
    }

    /**
     * Importe UNIQUEMENT les matchs à venir depuis fixtures.csv
     */
    @Transactional
    public String importUpcomingFixtures() {
        log.info("🔮 Récupération des futurs matchs depuis {}", FIXTURES_URL);
        int count = 0;
        int errorCount = 0;
        try {
            Reader reader = getReaderIgnoringSSL(FIXTURES_URL);
            List<FootballDataRow> rows = new CsvToBeanBuilder<FootballDataRow>(reader)
                    .withType(FootballDataRow.class).withSeparator(',').withIgnoreLeadingWhiteSpace(true).build().parse();
            reader.close();

            for (FootballDataRow row : rows) {
                try {
                    if (row.getDiv() == null) continue;
                    String leagueCode = DIV_TO_LEAGUE_CODE.get(row.getDiv());
                    if (leagueCode == null) continue;

                    String leagueName = resolveLeagueName(leagueCode);
                    League league = leagueRepository.findByName(leagueName).orElse(null);
                    if (league == null) continue;

                    Team home = resolveTeam(row.getHomeTeam(), league);
                    Team away = resolveTeam(row.getAwayTeam(), league);

                    LocalDate date = LocalDate.parse(row.getDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    List<MatchAnalysis> existingMatches = matchRepository.findByTeamIds(home.getId(), away.getId());
                    boolean exists = existingMatches.stream().anyMatch(m -> m.getMatchDate().toLocalDate().isEqual(date));

                    if (!exists) {
                        MatchAnalysis m = new MatchAnalysis();
                        m.setHomeTeam(home);
                        m.setAwayTeam(away);
                        m.setSeason(CURRENT_SEASON);

                        // --- CORRECTION HEURE (+1h pour la France) ---
                        LocalTime time = (row.getTime() != null && !row.getTime().isEmpty())
                                ? LocalTime.parse(row.getTime(), DateTimeFormatter.ofPattern("HH:mm"))
                                : LocalTime.of(20, 0);
                        m.setMatchDate(LocalDateTime.of(date, time.plusHours(1))); // Ajout +1h
                        // ---------------------------------------------

                        m.setOdds1(row.getB365H());
                        m.setOddsN(row.getB365D());
                        m.setOdds2(row.getB365A());
                        m.setOddsOver25(row.getB365O25());
                        m.setOddsUnder25(row.getB365U25());

                        m.setHomeScore(null); m.setAwayScore(null);
                        m.setHomeMatchStats(new MatchDetailStats());
                        m.setAwayMatchStats(new MatchDetailStats());
                        m.setHomeStats(new TeamStats());
                        m.setAwayStats(new TeamStats());

                        predictFutureMatch(m);
                        matchRepository.save(m);
                        count++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.warn("Erreur fixture ({} vs {}): {}", row.getHomeTeam(), row.getAwayTeam(), e.getMessage());
                }
            }
            return String.format("✅ %d matchs à venir importés (%d erreurs).", count, errorCount);
        } catch (Exception e) {
            log.error("Erreur import fixtures", e);
            return "Erreur: " + e.getMessage();
        }
    }

    /**
     * Importe l'historique des résultats (E0.csv, F1.csv...)
     */
    @Transactional
    public String importLeagueData(String leagueCode, boolean forceUpdate) {
        String csvUrl = LEAGUE_URLS.get(leagueCode);
        if (csvUrl == null) return "❌ Code ligue inconnu.";

        long start = System.currentTimeMillis();
        log.info("🚀 Import Historique {}...", leagueCode);

        try {
            Reader reader = getReaderIgnoringSSL(csvUrl);
            List<FootballDataRow> rows = new CsvToBeanBuilder<FootballDataRow>(reader)
                    .withType(FootballDataRow.class).withSeparator(',').withIgnoreLeadingWhiteSpace(true).build().parse();
            reader.close();

            String leagueName = resolveLeagueName(leagueCode);
            League league = getOrCreateLeague(leagueName, resolveCountry(leagueCode));

            // Cache local pour éviter des milliers de requêtes
            Map<String, Team> teamCache = teamRepository.findByLeague(league).stream().collect(Collectors.toMap(Team::getName, t -> t));

            // On charge les matchs existants pour cette saison
            Map<String, MatchAnalysis> existingMatchesMap = matchRepository.findByHomeTeamLeagueAndSeason(league, CURRENT_SEASON)
                    .stream().collect(Collectors.toMap(
                            m -> generateMatchKey(m.getHomeTeam(), m.getAwayTeam(), m.getMatchDate().toLocalDate()),
                            m -> m, (a, b) -> a));

            ImportStats stats = processRows(rows, league, teamCache, existingMatchesMap, forceUpdate);

            stats.teamsToRecalculate.forEach(teamStatsService::recalculateTeamStats);
            updateLeagueStats(league);

            return String.format("✅ %s : %d nouveaux, %d maj (%d ms).", leagueName, stats.importedCount, stats.updatedCount, System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error("Erreur Import " + leagueCode, e);
            return "Erreur : " + e.getMessage();
        }
    }

    @Transactional
    public String importAllLeagues(boolean forceUpdate) {
        StringBuilder report = new StringBuilder("--- Rapport Import Global ---\n");
        for (String code : LEAGUE_URLS.keySet()) {
            report.append(importLeagueData(code, forceUpdate)).append("\n");
        }
        report.append(importUpcomingFixtures()).append("\n");
        return report.toString();
    }

    public Set<String> getAvailableLeagues() { return LEAGUE_URLS.keySet(); }

    // --- LOGIQUE MÉTIER ---

    private ImportStats processRows(List<FootballDataRow> rows, League league,
            Map<String, Team> teamCache, Map<String, MatchAnalysis> existingMatches,
            boolean forceUpdate) {
        ImportStats stats = new ImportStats();

        for (FootballDataRow row : rows) {
            try {
                if(row.getDate() == null) continue;
                LocalDate date = LocalDate.parse(row.getDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));

                // Heure +1h
                LocalTime time = (row.getTime() != null)
                        ? LocalTime.parse(row.getTime(), DateTimeFormatter.ofPattern("HH:mm"))
                        : LocalTime.of(16, 0);
                LocalDateTime matchDateTime = LocalDateTime.of(date, time.plusHours(1));

                Team home = resolveTeamFromCache(row.getHomeTeam(), league, teamCache);
                Team away = resolveTeamFromCache(row.getAwayTeam(), league, teamCache);
                String matchKey = generateMatchKey(home, away, date);

                MatchAnalysis matchToSave;

                if (existingMatches.containsKey(matchKey)) {
                    MatchAnalysis existing = existingMatches.get(matchKey);

                    // Smart Update
                    boolean newScoreAvailable = (existing.getHomeScore() == null && row.getFTHG() != null);

                    if (forceUpdate || newScoreAvailable) {
                        matchToSave = existing;
                        stats.updatedCount++;
                    } else {
                        stats.skippedCount++;
                        continue; // On passe au suivant, le code en dessous n'est pas exécuté
                    }
                } else {
                    matchToSave = new MatchAnalysis();
                    matchToSave.setHomeTeam(home);
                    matchToSave.setAwayTeam(away);
                    matchToSave.setSeason(CURRENT_SEASON);
                    stats.importedCount++;
                }

                // Si on est ici, c'est forcément qu'on doit sauvegarder
                // Plus besoin de variable booléenne intermédiaire
                mapDataToMatch(matchToSave, row, matchDateTime);
                matchRepository.save(matchToSave);
                stats.teamsToRecalculate.add(home.getId());
                stats.teamsToRecalculate.add(away.getId());

            } catch (Exception e) {
                log.warn("Ligne ignorée : {}", e.getMessage());
            }
        }
        return stats;
    }

    private void mapDataToMatch(MatchAnalysis m, FootballDataRow row, LocalDateTime date) {
        m.setMatchDate(date);

        if (row.getFTHG() != null) {
            m.setHomeScore(row.getFTHG());
            m.setAwayScore(row.getFTAG());
            m.setHomeScoreHT(row.getHTHG());
            m.setAwayScoreHT(row.getHTAG());
            m.setReferee(row.getReferee());

            MatchDetailStats hs = (m.getHomeMatchStats() == null) ? new MatchDetailStats() : m.getHomeMatchStats();
            hs.setShots(row.getHS()); hs.setShotsOnTarget(row.getHST());
            hs.setCorners(row.getHC()); hs.setFouls(row.getHF());
            hs.setYellowCards(row.getHY()); hs.setRedCards(row.getHR());
            m.setHomeMatchStats(hs);

            MatchDetailStats as = (m.getAwayMatchStats() == null) ? new MatchDetailStats() : m.getAwayMatchStats();
            as.setShots(row.getAS()); as.setShotsOnTarget(row.getAST());
            as.setCorners(row.getAC()); as.setFouls(row.getAF());
            as.setYellowCards(row.getAY()); as.setRedCards(row.getAR());
            m.setAwayMatchStats(as);
        } else {
            // Match futur (pas de score)
            if(m.getHomeScore() == null) { // On ne nullifie pas si déjà existant, sauf si explicitement voulu
                m.setHomeScore(null); m.setAwayScore(null);
            }
            if(m.getHomeMatchStats() == null) m.setHomeMatchStats(new MatchDetailStats());
            if(m.getAwayMatchStats() == null) m.setAwayMatchStats(new MatchDetailStats());
        }

        m.setOdds1(row.getB365H());
        m.setOddsN(row.getB365D());
        m.setOdds2(row.getB365A());
        m.setOddsOver25(row.getB365O25());
        m.setOddsUnder25(row.getB365U25());

        if(m.getHomeStats() == null) m.setHomeStats(new TeamStats());
        if(m.getAwayStats() == null) m.setAwayStats(new TeamStats());

        if (m.getHomeScore() == null) predictFutureMatch(m);
    }

    private void predictFutureMatch(MatchAnalysis m) {
        // Optimisation : Utiliser les IDs pour éviter les problèmes Hibernate
        var h2h = matchRepository.findH2H(m.getHomeTeam(), m.getAwayTeam(), null);
        var homeHistory = matchRepository.findLastMatchesByTeam(m.getHomeTeam().getId());
        var awayHistory = matchRepository.findLastMatchesByTeam(m.getAwayTeam().getId());

        double leagueAvg = (m.getHomeTeam().getLeague() != null && m.getHomeTeam().getLeague().getAverageGoalsPerMatch() != null)
                ? m.getHomeTeam().getLeague().getAverageGoalsPerMatch() : 2.5;

        PredictionResult prediction = predictionEngineService.calculateMatchPrediction(
                m, h2h, homeHistory, awayHistory, leagueAvg
        );
        m.setPrediction(prediction);
    }

    private void updateLeagueStats(League league) {
        List<MatchAnalysis> matches = matchRepository.findByHomeTeamLeagueAndSeason(league, CURRENT_SEASON);
        if (matches.isEmpty()) return;

        double totalMatches = 0;
        double totalGoals = 0;
        int homeWins = 0, draws = 0, awayWins = 0, over25 = 0, btts = 0;

        for (MatchAnalysis m : matches) {
            if (m.getHomeScore() != null && m.getAwayScore() != null) {
                totalMatches++;
                int goals = m.getHomeScore() + m.getAwayScore();
                totalGoals += goals;
                if (m.getHomeScore() > m.getAwayScore()) homeWins++;
                else if (m.getHomeScore() < m.getAwayScore()) awayWins++;
                else draws++;
                if (goals > 2.5) over25++;
                if (m.getHomeScore() > 0 && m.getAwayScore() > 0) btts++;
            }
        }

        if (totalMatches > 0) {
            league.setAverageGoalsPerMatch(round(totalGoals / totalMatches));
            league.setPercentHomeWin(round((homeWins / totalMatches) * 100));
            league.setPercentDraw(round((draws / totalMatches) * 100));
            league.setPercentAwayWin(round((awayWins / totalMatches) * 100));
            league.setPercentOver2_5(round((over25 / totalMatches) * 100));
            league.setPercentBTTS(round((btts / totalMatches) * 100));
            leagueRepository.save(league);
            log.info("📊 Stats Ligue {} mises à jour ({} matchs).", league.getName(), (int)totalMatches);
        }
    }

    // --- HELPERS ---
    private Team resolveTeam(String csvName, League league) {
        String cleanName = TEAM_NAME_MAPPING.getOrDefault(csvName, csvName);
        return teamRepository.findByNameAndLeague(cleanName, league)
                .orElseGet(() -> teamRepository.save(new Team(cleanName, league)));
    }

    private Team resolveTeamFromCache(String csvName, League league, Map<String, Team> cache) {
        String cleanName = TEAM_NAME_MAPPING.getOrDefault(csvName, csvName);
        if (cache.containsKey(cleanName)) return cache.get(cleanName);
        Team newTeam = new Team(cleanName, league);
        newTeam = teamRepository.save(newTeam);
        cache.put(cleanName, newTeam);
        return newTeam;
    }

    private League getOrCreateLeague(String name, String country) {
        return leagueRepository.findByName(name).orElseGet(() -> leagueRepository.save(new League(name, country, "xx")));
    }

    private String resolveLeagueName(String code) {
        return switch(code) {
            case "PL" -> "Premier League"; case "L1" -> "Ligue 1"; case "LIGA" -> "La Liga";
            case "SERIEA" -> "Serie A"; case "BUNDES" -> "Bundesliga"; default -> "Unknown League";
        };
    }
    private String resolveCountry(String code) {
        return switch(code) {
            case "PL" -> "England"; case "L1" -> "France"; case "LIGA" -> "Spain";
            case "SERIEA" -> "Italy"; case "BUNDES" -> "Germany"; default -> "World";
        };
    }
    private String generateMatchKey(Team h, Team a, LocalDate date) {
        return h.getId() + "-" + a.getId() + "-" + date;
    }
    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private static class ImportStats { int importedCount=0; int updatedCount=0; int skippedCount=0; Set<Long> teamsToRecalculate = new HashSet<>(); }

    private Reader getReaderIgnoringSSL(String urlString) throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{ new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return null; }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
        }};
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());
        URL url = URI.create(urlString).toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sc.getSocketFactory());
        conn.setHostnameVerifier((hostname, session) -> true);

        // BOM Fix
        java.io.InputStream is = conn.getInputStream();
        java.io.PushbackInputStream pis = new java.io.PushbackInputStream(is, 3);
        byte[] header = new byte[3];
        int n = pis.read(header, 0, 3);
        if (n >= 3 && header[0] == (byte)0xEF && header[1] == (byte)0xBB && header[2] == (byte)0xBF) {
            // BOM skipped
        } else if (n > 0) {
            pis.unread(header, 0, n);
        }
        return new InputStreamReader(pis, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Data
    public static class FootballDataRow {
        @com.opencsv.bean.CsvBindByName(column = "Div") private String div;
        @com.opencsv.bean.CsvBindByName(column = "Date") private String date;
        @com.opencsv.bean.CsvBindByName(column = "Time") private String time;
        @com.opencsv.bean.CsvBindByName(column = "HomeTeam") private String homeTeam;
        @com.opencsv.bean.CsvBindByName(column = "AwayTeam") private String awayTeam;
        @com.opencsv.bean.CsvBindByName(column = "FTHG") private Integer FTHG;
        @com.opencsv.bean.CsvBindByName(column = "FTAG") private Integer FTAG;
        @com.opencsv.bean.CsvBindByName(column = "HTHG") private Integer HTHG;
        @com.opencsv.bean.CsvBindByName(column = "HTAG") private Integer HTAG;
        @com.opencsv.bean.CsvBindByName(column = "Referee") private String referee;
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
        @com.opencsv.bean.CsvBindByName(column = "B365<2.5") private Double B365U25;
    }
}
