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

    // --- CONFIGURATION BIG 5 ---
    // Mapping Code -> URL CSV (Saison 2025/2026)
    private static final Map<String, String> LEAGUE_URLS = Map.of(
            "PL", "https://www.football-data.co.uk/mmz4281/2526/E0.csv",    // Premier League
            "L1", "https://www.football-data.co.uk/mmz4281/2526/F1.csv",    // Ligue 1
            "LIGA", "https://www.football-data.co.uk/mmz4281/2526/SP1.csv",  // La Liga
            "SERIEA", "https://www.football-data.co.uk/mmz4281/2526/I1.csv", // Serie A
            "BUNDES", "https://www.football-data.co.uk/mmz4281/2526/D1.csv"  // Bundesliga
    );

    private static final String CURRENT_SEASON = "2025-2026";

    // Mapping noms CSV spécifiques -> Noms propres BDD
    private static final Map<String, String> TEAM_NAME_MAPPING = new HashMap<>();
    static {
        // Premier League
        TEAM_NAME_MAPPING.put("Man City", "Man City");
        TEAM_NAME_MAPPING.put("Man United", "Man United");
        TEAM_NAME_MAPPING.put("Spurs", "Tottenham");
        // Ligue 1
        TEAM_NAME_MAPPING.put("Paris SG", "PSG");
        TEAM_NAME_MAPPING.put("Marseille", "OM");
        TEAM_NAME_MAPPING.put("St Etienne", "Saint-Etienne");
        // Autres ligues à compléter au besoin...
    }

    /**
     * Méthode générique d'importation pour n'importe quelle ligue supportée.
     * @param leagueCode Code de la ligue (ex: "PL", "L1")
     * @param forceUpdate Si true, écrase les données des matchs existants.
     */
    @Transactional
    public String importLeagueData(String leagueCode, boolean forceUpdate) {
        String csvUrl = LEAGUE_URLS.get(leagueCode);
        if (csvUrl == null) {
            return "❌ Erreur : Code ligue '" + leagueCode + "' inconnu.";
        }

        long start = System.currentTimeMillis();
        log.info("🚀 Démarrage import {} depuis {}", leagueCode, csvUrl);

        try {
            // 1. Lecture du CSV (SSL Ignored pour éviter les erreurs de certificats Java locaux)
            Reader reader = getReaderIgnoringSSL(csvUrl);
            List<FootballDataRow> rows = new CsvToBeanBuilder<FootballDataRow>(reader)
                    .withType(FootballDataRow.class)
                    .withSeparator(',')
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();
            reader.close();

            // 2. Résolution / Création de la Ligue
            String leagueName = resolveLeagueName(leagueCode);
            String country = resolveCountry(leagueCode);
            League league = getOrCreateLeague(leagueName, country);

            // 3. Préparation Cache (Optimisation Perf)
            Map<String, Team> teamCache = teamRepository.findByLeague(league)
                    .stream()
                    .collect(Collectors.toMap(Team::getName, t -> t));

            Map<String, MatchAnalysis> existingMatchesMap = matchRepository.findByHomeTeamLeagueAndSeason(league, CURRENT_SEASON)
                    .stream()
                    .collect(Collectors.toMap(
                            m -> generateMatchKey(m.getHomeTeam(), m.getAwayTeam(), m.getMatchDate().toLocalDate()),
                            m -> m,
                            (existing, replacement) -> existing
                    ));

            // 4. Traitement des Lignes
            ImportStats stats = processRows(rows, league, teamCache, existingMatchesMap, forceUpdate);

            // 5. Recalcul Batch des Stats Équipes (Seulement celles impactées)
            log.info("🔄 Recalcul des stats pour {} équipes...", stats.teamsToRecalculate.size());
            stats.teamsToRecalculate.forEach(teamStatsService::recalculateTeamStats);

            // 6. Mise à jour des stats globales de la Ligue (Moyenne buts, % Home Win...)
            updateLeagueStats(league);

            long duration = System.currentTimeMillis() - start;
            return String.format("✅ Import %s terminé en %d ms. %d nouveaux, %d mis à jour, %d ignorés.",
                    leagueName, duration, stats.importedCount, stats.updatedCount, stats.skippedCount);

        } catch (Exception e) {
            log.error("❌ Erreur Import {}", leagueCode, e);
            return "Erreur : " + e.getMessage();
        }
    }

    /**
     * Importe tous les championnats configurés en une seule fois via la Map LEAGUE_URLS.
     */
    @Transactional
    public String importAllLeagues(boolean forceUpdate) {
        StringBuilder report = new StringBuilder();
        report.append("--- Rapport d'Import Global ---\n");
        long start = System.currentTimeMillis();

        // On parcourt toutes les clés (PL, L1, LIGA, etc.)
        for (String code : LEAGUE_URLS.keySet()) {
            report.append(String.format("[%s] : ", code));
            try {
                // On réutilise la logique unitaire
                String result = importLeagueData(code, forceUpdate);
                report.append(result).append("\n");
            } catch (Exception e) {
                report.append("ERREUR : ").append(e.getMessage()).append("\n");
            }
        }

        long duration = System.currentTimeMillis() - start;
        report.append(String.format("------------------------------\nTemps total : %d ms", duration));

        return report.toString();
    }

    // Pour rappel, voici un accesseur public pour récupérer les ligues dispo (utile pour l'IHM)
    public Set<String> getAvailableLeagues() {
        return LEAGUE_URLS.keySet();
    }

    // --- LOGIQUE MÉTIER ---

    private ImportStats processRows(List<FootballDataRow> rows, League league,
            Map<String, Team> teamCache,
            Map<String, MatchAnalysis> existingMatches,
            boolean forceUpdate) {
        ImportStats stats = new ImportStats();

        for (FootballDataRow row : rows) {
            try {
                // Parsing Date & Time
                // Note: football-data.co.uk formatte parfois la date différemment (dd/MM/yy ou dd/MM/yyyy).
                // Ici on assume dd/MM/yyyy. Une gestion d'erreur serait bienvenue.
                LocalDate date = LocalDate.parse(row.getDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                LocalTime time = row.getTime() != null ? LocalTime.parse(row.getTime(), DateTimeFormatter.ofPattern("HH:mm")) : LocalTime.of(16, 0);
                LocalDateTime matchDateTime = LocalDateTime.of(date, time);

                // Résolution Equipes
                Team home = resolveTeam(row.getHomeTeam(), league, teamCache);
                Team away = resolveTeam(row.getAwayTeam(), league, teamCache);

                String matchKey = generateMatchKey(home, away, date);
                MatchAnalysis matchToSave;

                if (existingMatches.containsKey(matchKey)) {
                    if (forceUpdate) {
                        matchToSave = existingMatches.get(matchKey);
                        stats.updatedCount++;
                    } else {
                        stats.skippedCount++;
                        continue;
                    }
                } else {
                    matchToSave = new MatchAnalysis();
                    matchToSave.setHomeTeam(home);
                    matchToSave.setAwayTeam(away);
                    matchToSave.setSeason(CURRENT_SEASON);
                    stats.importedCount++;
                }

                // Mapping des données CSV -> Entité JPA
                mapDataToMatch(matchToSave, row, matchDateTime);
                matchRepository.save(matchToSave);

                stats.teamsToRecalculate.add(home.getId());
                stats.teamsToRecalculate.add(away.getId());

            } catch (Exception e) {
                log.warn("Ligne ignorée (erreur parsing): {}", row, e);
            }
        }
        return stats;
    }

    private void mapDataToMatch(MatchAnalysis m, FootballDataRow row, LocalDateTime date) {
        m.setMatchDate(date);
        m.setHomeScore(row.getFTHG());
        m.setAwayScore(row.getFTAG());
        m.setHomeScoreHT(row.getHTHG());
        m.setAwayScoreHT(row.getHTAG());
        m.setReferee(row.getReferee());

        // Stats Home
        MatchDetailStats hs = (m.getHomeMatchStats() == null) ? new MatchDetailStats() : m.getHomeMatchStats();
        hs.setShots(row.getHS()); hs.setShotsOnTarget(row.getHST());
        hs.setCorners(row.getHC()); hs.setFouls(row.getHF());
        hs.setYellowCards(row.getHY()); hs.setRedCards(row.getHR());
        m.setHomeMatchStats(hs);

        // Stats Away
        MatchDetailStats as = (m.getAwayMatchStats() == null) ? new MatchDetailStats() : m.getAwayMatchStats();
        as.setShots(row.getAS()); as.setShotsOnTarget(row.getAST());
        as.setCorners(row.getAC()); as.setFouls(row.getAF());
        as.setYellowCards(row.getAY()); as.setRedCards(row.getAR());
        m.setAwayMatchStats(as);

        // Cotes (Bet365)
        m.setOdds1(row.getB365H());
        m.setOddsN(row.getB365D());
        m.setOdds2(row.getB365A());
        m.setOddsOver25(row.getB365O25());
        m.setOddsUnder25(row.getB365U25());

        // Init stats team vides si null (évite NPE)
        if(m.getHomeStats() == null) m.setHomeStats(new TeamStats());
        if(m.getAwayStats() == null) m.setAwayStats(new TeamStats());
    }

    private void updateLeagueStats(League league) {
        List<MatchAnalysis> matches = matchRepository.findByHomeTeamLeagueAndSeason(league, CURRENT_SEASON);
        if (matches.isEmpty()) return;

        double totalMatches = matches.size();
        double totalGoals = 0;
        int homeWins = 0, draws = 0, awayWins = 0;
        int over25 = 0, btts = 0;

        for (MatchAnalysis m : matches) {
            if (m.getHomeScore() == null || m.getAwayScore() == null) continue; // Sécurité

            int goals = m.getHomeScore() + m.getAwayScore();
            totalGoals += goals;

            if (m.getHomeScore() > m.getAwayScore()) homeWins++;
            else if (m.getHomeScore() < m.getAwayScore()) awayWins++;
            else draws++;

            if (goals > 2.5) over25++;
            if (m.getHomeScore() > 0 && m.getAwayScore() > 0) btts++;
        }

        // Calcul des % globaux
        league.setAverageGoalsPerMatch(round(totalGoals / totalMatches));
        league.setPercentHomeWin(round((homeWins / totalMatches) * 100));
        league.setPercentDraw(round((draws / totalMatches) * 100));
        league.setPercentAwayWin(round((awayWins / totalMatches) * 100));
        league.setPercentOver2_5(round((over25 / totalMatches) * 100));
        league.setPercentBTTS(round((btts / totalMatches) * 100));

        leagueRepository.save(league);
        log.info("📊 Stats Ligue {} mises à jour ({} matchs)", league.getName(), totalMatches);
    }

    // --- HELPERS & UTILITAIRES ---

    private Team resolveTeam(String csvName, League league, Map<String, Team> cache) {
        String cleanName = TEAM_NAME_MAPPING.getOrDefault(csvName, csvName);
        if (cache.containsKey(cleanName)) return cache.get(cleanName);

        Team newTeam = new Team(cleanName, league);
        newTeam = teamRepository.save(newTeam);
        cache.put(cleanName, newTeam);
        return newTeam;
    }

    private League getOrCreateLeague(String name, String country) {
        return leagueRepository.findByName(name)
                .orElseGet(() -> leagueRepository.save(new League(name, country, "xx")));
    }

    private String resolveLeagueName(String code) {
        return switch(code) {
            case "PL" -> "Premier League";
            case "L1" -> "Ligue 1";
            case "LIGA" -> "La Liga";
            case "SERIEA" -> "Serie A";
            case "BUNDES" -> "Bundesliga";
            default -> "Unknown League";
        };
    }

    private String resolveCountry(String code) {
        return switch(code) {
            case "PL" -> "England";
            case "L1" -> "France";
            case "LIGA" -> "Spain";
            case "SERIEA" -> "Italy";
            case "BUNDES" -> "Germany";
            default -> "World";
        };
    }

    private String generateMatchKey(Team h, Team a, LocalDate date) {
        return h.getId() + "-" + a.getId() + "-" + date;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // Classe interne pour le reporting
    private static class ImportStats {
        int importedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        Set<Long> teamsToRecalculate = new HashSet<>();
    }

    // --- SSL BYPASS (Nécessaire pour certaines configs locales/legacy) ---
    private Reader getReaderIgnoringSSL(String urlString) throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());
        URL url = java.net.URI.create(urlString).toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sc.getSocketFactory());
        conn.setHostnameVerifier((hostname, session) -> true);
        return new InputStreamReader(conn.getInputStream());
    }

    // --- DTO CSV ---
    @Data
    public static class FootballDataRow {
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
