package com.tony.sportsAnalytics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class XgScraperService {
    public Map<String, Double> scrapeXgFromFbRef(String leagueUrl) {
        try {
            // Connexion au site FBRef (Respecter les délais pour ne pas être banni)
            org.jsoup.nodes.Document doc = org.jsoup.connect(leagueUrl).get();
            // Sélection de la table "Squad Overall Stats"
            org.jsoup.select.Elements rows = doc.select("table#stats_squads_standard_for tbody tr");

            Map<String, Double> teamXgMap = new HashMap<>();
            for (org.jsoup.nodes.Element row : rows) {
                String teamName = row.select("th[data-stat=team]").text();
                double xGPerMatch = Double.parseDouble(row.select("td[data-stat=xg_per90]").text());
                teamXgMap.put(teamName, xGPerMatch);
            }
            return teamXgMap;
        } catch (Exception e) {
            log.error("Erreur lors du scraping xG", e);
            return Collections.emptyMap();
        }
    }
}
