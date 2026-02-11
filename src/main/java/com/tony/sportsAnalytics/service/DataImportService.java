package com.tony.sportsAnalytics.service;

import com.opencsv.bean.CsvToBeanBuilder;
import com.tony.sportsAnalytics.model.*;
import com.tony.sportsAnalytics.repository.*;
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
    private final PredictionEvaluationService evaluationService;
    private final XgScraperService xgScraperService;

    // --- CONFIGURATION CONSTANTES ---
    private static final String BASE_URL = "https://www.football-data.co.uk/mmz4281/";
    private static final String FIXTURES_URL = "https://www.football-data.co.uk/fixtures.csv";

    // Mapping Code Ligue -> Fichier CSV
    private static final Map<String, String> LEAGUE_FILES = Map.of(
            "PL", "E0.csv",
            "L1", "F1.csv",
            "LIGA", "SP1.csv",
            "SERIEA", "I1.csv",
            "BUNDES", "D1.csv"
    );

    private static final Map<String, String> FBREF_LEAGUE_URLS = Map.of(
            "PL", "https://fbref.com/en/comps/9/stats/Premier-League-Stats",
            "L1", "https://fbref.com/en/comps/13/stats/Ligue-1-Stats",
            "LIGA", "https://fbref.com/en/comps/12/stats/La-Liga-Stats",
            "SERIEA", "https://fbref.com/en/comps/11/stats/Serie-A-Stats",
            "BUNDES", "https://fbref.com/en/comps/20/stats/Bundesliga-Stats"
    );

    private static final Map<String, String> DIV_TO_LEAGUE_CODE = Map.of(
            "E0", "PL", "F1", "L1", "SP1", "LIGA", "I1", "SERIEA", "D1", "BUNDES"
    );

    // Saison Actuelle (pour les URLs)
    private static final String CURRENT_SEASON_CODE = "2526"; // 2025-2026
    private static final String CURRENT_SEASON_LABEL = "2025-2026";

    // Historique à importer (Ordre Chronologique pour Elo)
    // 2122 = 2021-2022, etc.
    private static final List<String> HISTORICAL_SEASONS = List.of("2122", "2223", "2324", "2425");

    // Mapping noms CSV spécifiques -> Noms propres BDD
    private static final Map<String, String> TEAM_NAME_MAPPING = new HashMap<>();
    static {
        TEAM_NAME_MAPPING.put("Man City", "Man City");
        TEAM_NAME_MAPPING.put("Man United", "Man United");
        TEAM_NAME_MAPPING.put("Spurs", "Tottenham");
        TEAM_NAME_MAPPING.put("Paris SG", "PSG");
        TEAM_NAME_MAPPING.put("Marseille", "OM");
        TEAM_NAME_MAPPING.put("St Etienne", "Saint-Etienne");
        TEAM_NAME_MAPPING.put("Atletico Madrid", "Atl. Madrid");
        TEAM_NAME_MAPPING.put("Betis", "Real Betis");
    }

    // Référentiel GPS des principaux stades (Latitude, Longitude)
    private static final Map<String, double[]> STADIUM_COORDS = new HashMap<>();
    static {
        // Angleterre
        STADIUM_COORDS.put("Man City", new double[]{53.4831, -2.2004}); // Etihad
        STADIUM_COORDS.put("Liverpool", new double[]{53.4308, -2.9608}); // Anfield
        STADIUM_COORDS.put("Arsenal", new double[]{51.5549, -0.1084});  // Emirates
        STADIUM_COORDS.put("Man United", new double[]{53.4631, -2.2913});
        STADIUM_COORDS.put("Chelsea", new double[]{51.4816, -0.1909});
        STADIUM_COORDS.put("Tottenham", new double[]{51.6042, -0.0662});

        // France
        STADIUM_COORDS.put("PSG", new double[]{48.8414, 2.2530});       // Parc des Princes
        STADIUM_COORDS.put("Marseille", new double[]{43.2698, 5.3959}); // Vélodrome
        STADIUM_COORDS.put("Lyon", new double[]{45.7653, 4.9820});
        STADIUM_COORDS.put("Lille", new double[]{50.6119, 3.1305});

        // Espagne
        STADIUM_COORDS.put("Real Madrid", new double[]{40.4530, -3.6883});
        STADIUM_COORDS.put("Barcelona", new double[]{41.3809, 2.1228});
        STADIUM_COORDS.put("Atl. Madrid", new double[]{40.4362, -3.5995});

        // Italie
        STADIUM_COORDS.put("Juventus", new double[]{45.1096, 7.6412});
        STADIUM_COORDS.put("Milan", new double[]{45.4781, 9.1240});     // San Siro
        STADIUM_COORDS.put("Inter", new double[]{45.4781, 9.1240});

        // Allemagne
        STADIUM_COORDS.put("Bayern Munich", new double[]{48.2188, 11.6247});
        STADIUM_COORDS.put("Dortmund", new double[]{51.4926, 7.4519});
        STADIUM_COORDS.put("Leverkusen", new double[]{51.0383, 7.0022});
    }

    /**
     * IMPORT MASSIF HISTORIQUE (Saisons passées)
     * À lancer une fois pour peupler la BDD et stabiliser les ratings Elo.
     */
    @Transactional
    public String importFullHistory() {
        StringBuilder report = new StringBuilder("--- IMPORT HISTORIQUE GLOBAL ---\n");
        long start = System.currentTimeMillis();

        // 1. On parcourt les saisons chronologiquement
        for (String seasonCode : HISTORICAL_SEASONS) {
            String seasonLabel = "20" + seasonCode.substring(0, 2) + "-20" + seasonCode.substring(2);
            report.append("\n=== SAISON ").append(seasonLabel).append(" ===\n");

            for (String leagueCode : LEAGUE_FILES.keySet()) {
                String fileName = LEAGUE_FILES.get(leagueCode);
                String url = BASE_URL + seasonCode + "/" + fileName;

                try {
                    // On force l'update pour l'historique pour être sûr d'avoir les données clean
                    String res = processImport(url, leagueCode, seasonLabel, true);
                    report.append(String.format("[%s] %s\n", leagueCode, res));
                } catch (Exception e) {
                    report.append(String.format("[%s] ERREUR: %s\n", leagueCode, e.getMessage()));
                    log.error("Erreur import historique {} {}", seasonLabel, leagueCode, e);
                }
            }
        }

        long duration = (System.currentTimeMillis() - start) / 1000;
        report.append("\n✅ Import terminé en ").append(duration).append("s");
        return report.toString();
    }

    public void enrichTeamsWithAdvancedStats(String leagueUrl) {
        // 1. Scraper les xG/xGA sur FBRef
        Map<String, XgScraperService.TeamXgMetrics> advancedStats = xgScraperService.scrapeAdvancedMetrics(leagueUrl);

        advancedStats.forEach((scrapedName, metrics) -> {
            // On essaie de trouver l'équipe en gérant les variations de noms
            String cleanName = TEAM_NAME_MAPPING.getOrDefault(scrapedName, scrapedName);

            teamRepository.findByName(cleanName).ifPresent(team -> {
                TeamStats stats = team.getCurrentStats();

                // Si pas de stats, on en crée une (Clean Code)
                if (stats == null) {
                    stats = new TeamStats();
                    team.setCurrentStats(stats);
                }

                // Injection de l'xG réel observé sur FBRef
                stats.setXG(metrics.xG());
                // On peut aussi stocker l'xGA (Expected Goals Against) si tu l'as ajouté à TeamStats
                 stats.setXGA(metrics.xGA());

                teamRepository.save(team);
                log.debug("✅ xG mis à jour pour {}", team.getName());
            });
        });
    }

    /**
     * Importe l'historique des résultats de la saison EN COURS (2526)
     * Appelée par l'admin via "Importer Premier League" etc.
     */
    @Transactional
    public String importLeagueData(String leagueCode, boolean forceUpdate) {
        if (!LEAGUE_FILES.containsKey(leagueCode)) return "❌ Code ligue inconnu.";

        String fileName = LEAGUE_FILES.get(leagueCode);
        String url = BASE_URL + CURRENT_SEASON_CODE + "/" + fileName;

        try {
            // 1. Import des résultats de base (CSV)
            String csvResult = processImport(url, leagueCode, CURRENT_SEASON_LABEL, forceUpdate);

            // 2. Enrichissement avec les stats avancées (Scraping xG)
            if (FBREF_LEAGUE_URLS.containsKey(leagueCode)) {
                log.info("📊 Enrichissement des xG pour la ligue {}", leagueCode);
                enrichTeamsWithAdvancedStats(FBREF_LEAGUE_URLS.get(leagueCode));
            }

            return csvResult + " + xG synchronisés.";
        } catch (Exception e) {
            log.error("Erreur Import {}", leagueCode, e);
            return "Erreur : " + e.getMessage();
        }
    }

    /**
     * Méthode générique interne pour traiter un fichier CSV de saison
     */
    private String processImport(String csvUrl, String leagueCode, String seasonLabel, boolean forceUpdate) throws Exception {
        log.info("📥 Chargement {} ({}) depuis {}", leagueCode, seasonLabel, csvUrl);

        Reader reader = getReaderIgnoringSSL(csvUrl);
        List<FootballDataRow> rows = new CsvToBeanBuilder<FootballDataRow>(reader)
                .withType(FootballDataRow.class).withSeparator(',').withIgnoreLeadingWhiteSpace(true).build().parse();
        reader.close();

        String leagueName = resolveLeagueName(leagueCode);
        League league = getOrCreateLeague(leagueName, resolveCountry(leagueCode));

        // Cache local
        Map<String, Team> teamCache = teamRepository.findByLeague(league).stream().collect(Collectors.toMap(Team::getName, t -> t));

        // Matchs existants pour cette saison spécifique
        Map<String, MatchAnalysis> existingMatchesMap = matchRepository.findByHomeTeamLeagueAndSeason(league, seasonLabel)
                .stream().collect(Collectors.toMap(
                        m -> generateMatchKey(m.getHomeTeam(), m.getAwayTeam(), m.getMatchDate().toLocalDate()),
                        m -> m, (a, b) -> a));

        ImportStats stats = processRows(rows, league, teamCache, existingMatchesMap, forceUpdate, seasonLabel);

        // On ne recalcule les stats globales que si c'est la saison en cours (gain de temps)
        if (seasonLabel.equals(CURRENT_SEASON_LABEL)) {
            stats.teamsToRecalculate.forEach(teamStatsService::recalculateTeamStats);
            updateLeagueStats(league, seasonLabel);
        }

        return String.format("%d importés, %d mis à jour.", stats.importedCount, stats.updatedCount);
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
                    // Utilisation de findByTeamIds pour éviter le bug "pk is null"
                    List<MatchAnalysis> existingMatches = matchRepository.findByTeamIds(home.getId(), away.getId());
                    boolean exists = existingMatches.stream().anyMatch(m -> m.getMatchDate().toLocalDate().isEqual(date));

                    if (!exists) {
                        MatchAnalysis m = new MatchAnalysis();
                        m.setHomeTeam(home);
                        m.setAwayTeam(away);
                        m.setSeason(CURRENT_SEASON_LABEL);

                        // Correction Heure (+1h)
                        LocalTime time = (row.getTime() != null && !row.getTime().isEmpty())
                                ? LocalTime.parse(row.getTime(), DateTimeFormatter.ofPattern("HH:mm"))
                                : LocalTime.of(20, 0);
                        m.setMatchDate(LocalDateTime.of(date, time.plusHours(1)));

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

    @Transactional
    public String importAllLeagues(boolean forceUpdate) {
        StringBuilder report = new StringBuilder("--- Rapport Import Saison En Cours ---\n");
        for (String code : LEAGUE_FILES.keySet()) {
            report.append(importLeagueData(code, forceUpdate)).append("\n");
        }
        report.append(importUpcomingFixtures()).append("\n");
        return report.toString();
    }

    public Set<String> getAvailableLeagues() { return LEAGUE_FILES.keySet(); }

    // --- LOGIQUE MÉTIER ---

    private ImportStats processRows(List<FootballDataRow> rows, League league,
            Map<String, Team> teamCache, Map<String, MatchAnalysis> existingMatches,
            boolean forceUpdate, String seasonLabel) {
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

                    // Smart Update: Si le match existe sans score et que le CSV en a un
                    boolean newScoreAvailable = (existing.getHomeScore() == null && row.getFTHG() != null);

                    if (forceUpdate || newScoreAvailable) {
                        matchToSave = existing;
                        stats.updatedCount++;
                    } else {
                        stats.skippedCount++;
                        continue; // On passe au suivant
                    }
                } else {
                    matchToSave = new MatchAnalysis();
                    matchToSave.setHomeTeam(home);
                    matchToSave.setAwayTeam(away);
                    matchToSave.setSeason(seasonLabel); // Utilise la saison passée en paramètre
                    stats.importedCount++;
                }

                // Pas de booléen 'shouldSave', on exécute direct si on n'a pas fait continue
                mapDataToMatch(matchToSave, row, matchDateTime);
                matchRepository.save(matchToSave);

                // On ne marque pour recalcul que si c'est la saison courante (optimisation)
                if (seasonLabel.equals(CURRENT_SEASON_LABEL)) {
                    stats.teamsToRecalculate.add(home.getId());
                    stats.teamsToRecalculate.add(away.getId());
                }

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
            if(m.getHomeScore() == null) { m.setHomeScore(null); m.setAwayScore(null); }
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

        // On prédit aussi pour l'historique (pour le backtesting)
        if (m.getHomeScore() == null || m.getPrediction() == null) {
            predictFutureMatch(m);
        }

        // Si on a un score et une prédiction existante, on évalue la performance
        if (m.getHomeScore() != null && m.getPrediction() != null) {
            evaluationService.evaluatePrediction(m);
        }
    }

    private void predictFutureMatch(MatchAnalysis m) {
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

    private void updateLeagueStats(League league, String season) {
        List<MatchAnalysis> matches = matchRepository.findByHomeTeamLeagueAndSeason(league, season);
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
            log.info("📊 Stats Ligue {} ({}) mises à jour.", league.getName(), season);
        }
    }

    // --- HELPERS ---
    private Team resolveTeam(String csvName, League league) {
        String cleanName = TEAM_NAME_MAPPING.getOrDefault(csvName, csvName);

        return teamRepository.findByNameAndLeague(cleanName, league)
                .orElseGet(() -> {
                    Team t = new Team(cleanName, league);

                    // Injection des coordonnées si connues
                    if (STADIUM_COORDS.containsKey(cleanName)) {
                        double[] gps = STADIUM_COORDS.get(cleanName);
                        t.setLatitude(gps[0]);
                        t.setLongitude(gps[1]);
                    }

                    return teamRepository.save(t);
                });
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

    // --- READER SECURISE (BOM + SSL) ---
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
            // BOM detected and skipped
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
