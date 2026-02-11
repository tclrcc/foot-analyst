package com.tony.sportsAnalytics.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import java.util.*;

import static java.lang.Double.parseDouble;

@Service
@Slf4j
public class XgScraperService {

    public Map<String, TeamXgMetrics> scrapeAdvancedMetrics(String leagueUrl) {
        Map<String, TeamXgMetrics> metrics = new HashMap<>();
        try {
            Document doc = Jsoup.connect(leagueUrl).get();
            Elements rows = doc.select("table#stats_squads_standard_for tbody tr");

            for (Element row : rows) {
                String teamName = row.select("th[data-stat=team]").text();
                double xG = parseDouble(row.select("td[data-stat=xg]").text());
                double xGA = parseDouble(row.select("td[data-stat=xg_against]").text());

                // Simulation de récupération PPDA et Field Tilt (colonnes souvent dans 'Possession' ou 'Misc')
                // Pour l'exemple, nous restons sur la table standard mais tu peux chaîner les URLs
                double ppda = parseDouble(row.select("td[data-stat=blocks]").text()) > 0 ? 12.0 : 10.5; // Logique à affiner selon FBRef
                double fieldTilt = 50.0; // Valeur par défaut

                metrics.put(teamName, new TeamXgMetrics(xG, xGA, ppda, fieldTilt));
            }
        } catch (Exception e) {
            log.error("Échec du scraping FBRef: {}", e.getMessage());
        }
        return metrics;
    }

    public record TeamXgMetrics(double xG, double xGA, double ppda, double fieldTilt) {}
}
