package com.tony.sportsAnalytics.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@Slf4j
public class XgScraperService {

    public Map<String, TeamXgMetrics> scrapeAdvancedMetrics(String leagueUrl) {
        Map<String, TeamXgMetrics> metrics = new HashMap<>();
        try {
            Document doc = Jsoup.connect(leagueUrl).get();
            // Sélecteur précis pour la table standard de FBRef
            Elements rows = doc.select("table#stats_squads_standard_for tbody tr");

            for (Element row : rows) {
                String teamName = row.select("th[data-stat=team]").text();
                double xG = Double.parseDouble(row.select("td[data-stat=xg]").text());
                double xGA = Double.parseDouble(row.select("td[data-stat=xg_against]").text());

                metrics.put(teamName, new TeamXgMetrics(xG, xGA));
            }
        } catch (Exception e) {
            log.error("Échec du scraping FBRef: {}", e.getMessage());
        }
        return metrics;
    }

    public record TeamXgMetrics(double xG, double xGA) {}
}
