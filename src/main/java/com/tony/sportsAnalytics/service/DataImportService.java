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
import java.io.Reader;
import java.io.InputStreamReader;
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

    // Mapping noms CSV -> BDD
    private static final Map<String, String> TEAM_NAME_MAPPING = new HashMap<>();
    static {
        TEAM_NAME_MAPPING.put("Man City", "Man City");
        TEAM_NAME_MAPPING.put("Man United", "Man United");
        TEAM_NAME_MAPPING.put("Nott'm Forest", "Nottingham Forest");
        TEAM_NAME_MAPPING.put("Spurs", "Tottenham");
        TEAM_NAME_MAPPING.put("Wolves", "Wolverhampton");
        TEAM_NAME_MAPPING.put("Leicester", "Leicester City");
        TEAM_NAME_MAPPING.put("Ipswich", "Ipswich Town");
        TEAM_NAME_MAPPING.put("Newcastle", "Newcastle United");
        // Ajouter d'autres mappings au besoin
    }

    @Transactional
    public String importPremierLeagueData(boolean forceUpdate) {
        String csvUrl = "https://www.football-data.co.uk/mmz4281/2526/E0.csv";
        long start = System.currentTimeMillis();

        try {
            Reader reader = getReaderIgnoringSSL(csvUrl);
            List<FootballDataRow> rows = new CsvToBeanBuilder<FootballDataRow>(reader)
                    .withType(FootballDataRow.class)
                    .withSeparator(',')
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();
            reader.close();

            // 1. Initialisation Ligue
            League league = getOrCreateLeague("Premier League", "England");

            // 2. Pré-chargement pour Performance (Cache des équipes et matchs existants)
            // On évite de faire 1 requête SQL par ligne pour vérifier l'existence
            Map<String, Team> teamCache = teamRepository.findByLeague(league)
                    .stream()
                    .collect(Collectors.toMap(Team::getName, t -> t));

            // CHANGEMENT ICI : On stocke l'objet MatchAnalysis entier dans la Map, pas juste une clé String
            // Cela permet de récupérer l'entité existante pour la mettre à jour
            Map<String, MatchAnalysis> existingMatchesMap = matchRepository.findByHomeTeamLeagueAndSeason(league, "2025-2026")
                    .stream()
                    .collect(Collectors.toMap(
                            m -> m.getHomeTeam().getId() + "-" + m.getAwayTeam().getId() + "-" + m.getMatchDate().toLocalDate(),
                            m -> m,
                            (existing, replacement) -> existing // En cas de doublon en base, on garde le premier
                    ));

            Set<Long> teamsToRecalculate = new HashSet<>();
            int importedCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;

            log.info("Début du traitement de {} lignes...", rows.size());

            for (FootballDataRow row : rows) {
                // Parsing Date & Time
                LocalDate date = LocalDate.parse(row.getDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                LocalTime time = row.getTime() != null ? LocalTime.parse(row.getTime(), DateTimeFormatter.ofPattern("HH:mm")) : LocalTime.of(16, 0);
                LocalDateTime matchDateTime = LocalDateTime.of(date, time);

                // Résolution Equipes (Via Cache ou Création)
                Team home = resolveTeam(row.getHomeTeam(), league, teamCache);
                Team away = resolveTeam(row.getAwayTeam(), league, teamCache);

                // Vérification Doublon (En mémoire -> Très rapide)
                String matchKey = home.getId() + "-" + away.getId() + "-" + date;

                MatchAnalysis matchToSave;

                if (existingMatchesMap.containsKey(matchKey)) {
                    // Le match existe déjà
                    if (forceUpdate) {
                        // MODE ÉCRASEMENT : On récupère l'existant
                        matchToSave = existingMatchesMap.get(matchKey);
                        updatedCount++;
                    } else {
                        // MODE SÉCURISÉ : On passe
                        skippedCount++;
                        continue;
                    }
                } else {
                    // NOUVEAU MATCH
                    matchToSave = new MatchAnalysis();
                    matchToSave.setHomeTeam(home);
                    matchToSave.setAwayTeam(away);
                    matchToSave.setSeason("2025-2026");
                    importedCount++;
                }

                // On met à jour les données (que ce soit un new ou un update)
                // J'ai extrait la logique de remplissage dans une méthode 'mapDataToMatch' pour éviter la duplication
                mapDataToMatch(matchToSave, row, matchDateTime);

                matchRepository.save(matchToSave);

                // On marque pour recalcul
                teamsToRecalculate.add(home.getId());
                teamsToRecalculate.add(away.getId());
            }

            // 3. Recalcul des stats d'équipe (Batch)
            log.info("Recalcul des stats pour {} équipes...", teamsToRecalculate.size());
            teamsToRecalculate.forEach(teamStatsService::recalculateTeamStats);

            // 4. Calcul des stats globales de la Ligue
            updateLeagueStats(league);

            long duration = System.currentTimeMillis() - start;
            return String.format("Import terminé en %d ms. %d nouveaux matchs, %d ignorés.", duration, importedCount, skippedCount);

        } catch (Exception e) {
            log.error("Erreur Import", e);
            return "Erreur : " + e.getMessage();
        }
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
        hs.setShots(row.getHS());
        hs.setShotsOnTarget(row.getHST());
        hs.setCorners(row.getHC());
        hs.setFouls(row.getHF());
        hs.setYellowCards(row.getHY());
        hs.setRedCards(row.getHR());
        m.setHomeMatchStats(hs);

        // Stats Away
        MatchDetailStats as = (m.getAwayMatchStats() == null) ? new MatchDetailStats() : m.getAwayMatchStats();
        as.setShots(row.getAS());
        as.setShotsOnTarget(row.getAST());
        as.setCorners(row.getAC());
        as.setFouls(row.getAF());
        as.setYellowCards(row.getAY());
        as.setRedCards(row.getAR());
        m.setAwayMatchStats(as);

        // Cotes
        m.setOdds1(row.getB365H());
        m.setOddsN(row.getB365D());
        m.setOdds2(row.getB365A());
        m.setOddsOver25(row.getB365O25());
        m.setOddsUnder25(row.getB365U25());

        // Init stats si null
        if(m.getHomeStats() == null) m.setHomeStats(new TeamStats());
        if(m.getAwayStats() == null) m.setAwayStats(new TeamStats());
    }

    private void createAndSaveMatch(FootballDataRow row, LocalDateTime date, Team home, Team away, League league) {
        MatchAnalysis m = new MatchAnalysis();
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setMatchDate(date);
        m.setSeason("2025-2026");

        // Scores FT
        m.setHomeScore(row.getFTHG());
        m.setAwayScore(row.getFTAG());

        // Scores HT (Nouveau)
        m.setHomeScoreHT(row.getHTHG());
        m.setAwayScoreHT(row.getHTAG());

        // Arbitre (Nouveau)
        m.setReferee(row.getReferee());

        // Stats Détaillées
        MatchDetailStats hs = new MatchDetailStats();
        hs.setShots(row.getHS());
        hs.setShotsOnTarget(row.getHST());
        hs.setCorners(row.getHC());
        hs.setFouls(row.getHF());
        hs.setYellowCards(row.getHY());
        hs.setRedCards(row.getHR());

        MatchDetailStats as = new MatchDetailStats();
        as.setShots(row.getAS());
        as.setShotsOnTarget(row.getAST());
        as.setCorners(row.getAC());
        as.setFouls(row.getAF());
        as.setYellowCards(row.getAY());
        as.setRedCards(row.getAR());

        m.setHomeMatchStats(hs);
        m.setAwayMatchStats(as);

        // Cotes
        m.setOdds1(row.getB365H());
        m.setOddsN(row.getB365D());
        m.setOdds2(row.getB365A());
        m.setOddsOver25(row.getB365O25());
        m.setOddsUnder25(row.getB365U25()); // Nouveau champ à ajouter dans Entity

        // Init stats vides
        m.setHomeStats(new TeamStats());
        m.setAwayStats(new TeamStats());

        matchRepository.save(m);
    }

    /**
     * Calcule les moyennes du championnat (Buts, %, etc.)
     */
    private void updateLeagueStats(League league) {
        List<MatchAnalysis> matches = matchRepository.findByHomeTeamLeagueAndSeason(league, "2025-2026");

        if (matches.isEmpty()) return;

        double totalMatches = matches.size();
        double totalGoals = 0;
        int homeWins = 0, draws = 0, awayWins = 0;
        int over25 = 0, btts = 0;

        for (MatchAnalysis m : matches) {
            int goals = m.getHomeScore() + m.getAwayScore();
            totalGoals += goals;

            if (m.getHomeScore() > m.getAwayScore()) homeWins++;
            else if (m.getHomeScore() < m.getAwayScore()) awayWins++;
            else draws++;

            if (goals > 2.5) over25++;
            if (m.getHomeScore() > 0 && m.getAwayScore() > 0) btts++;
        }

        league.setAverageGoalsPerMatch(round(totalGoals / totalMatches));
        league.setPercentHomeWin(round((homeWins / totalMatches) * 100));
        league.setPercentDraw(round((draws / totalMatches) * 100));
        league.setPercentAwayWin(round((awayWins / totalMatches) * 100));
        league.setPercentOver2_5(round((over25 / totalMatches) * 100));
        league.setPercentBTTS(round((btts / totalMatches) * 100));

        leagueRepository.save(league);
        log.info("Stats de la Ligue mises à jour : {} buts/match moy.", league.getAverageGoalsPerMatch());
    }

    private Team resolveTeam(String csvName, League league, Map<String, Team> cache) {
        String cleanName = TEAM_NAME_MAPPING.getOrDefault(csvName, csvName);

        if (cache.containsKey(cleanName)) {
            return cache.get(cleanName);
        }

        // Création à la volée
        Team newTeam = new Team();
        newTeam.setName(cleanName);
        newTeam.setLeague(league);
        newTeam = teamRepository.save(newTeam);

        cache.put(cleanName, newTeam); // Mise à jour cache
        return newTeam;
    }

    private League getOrCreateLeague(String name, String country) {
        return leagueRepository.findByName(name)
                .orElseGet(() -> {
                    League l = new League();
                    l.setName(name);
                    l.setCountry(country);
                    return leagueRepository.save(l);
                });
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // --- SSL IGNORE (Identique à avant) ---
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
        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sc.getSocketFactory());
        conn.setHostnameVerifier((hostname, session) -> true);
        return new InputStreamReader(conn.getInputStream());
    }

    // --- DTO CSV COMPLET ---
    @Data
    public static class FootballDataRow {
        @com.opencsv.bean.CsvBindByName(column = "Date") private String date;
        @com.opencsv.bean.CsvBindByName(column = "Time") private String time; // NOUVEAU
        @com.opencsv.bean.CsvBindByName(column = "HomeTeam") private String homeTeam;
        @com.opencsv.bean.CsvBindByName(column = "AwayTeam") private String awayTeam;

        @com.opencsv.bean.CsvBindByName(column = "FTHG") private Integer FTHG;
        @com.opencsv.bean.CsvBindByName(column = "FTAG") private Integer FTAG;

        // MI-TEMPS
        @com.opencsv.bean.CsvBindByName(column = "HTHG") private Integer HTHG; // NOUVEAU
        @com.opencsv.bean.CsvBindByName(column = "HTAG") private Integer HTAG; // NOUVEAU

        @com.opencsv.bean.CsvBindByName(column = "Referee") private String referee; // NOUVEAU

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
        @com.opencsv.bean.CsvBindByName(column = "B365>2.5", required = false) private Double B365O25;
        @com.opencsv.bean.CsvBindByName(column = "B365<2.5", required = false) private Double B365U25; // NOUVEAU
    }
}
