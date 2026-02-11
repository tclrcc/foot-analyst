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

                // Extraction réelle (exemple de sélecteur pour FBRef)
                double touchesInFinalThird = parseDouble(row.select("td[data-stat=touches_att_3rd]").text());
                double fieldTilt = (touchesInFinalThird / 500.0) * 100.0; // Normalisation approximative

                metrics.put(teamName, new TeamXgMetrics(xG, xGA, 10.5, fieldTilt));
            }
        } catch (Exception e) {
            log.error("Échec du scraping FBRef: {}", e.getMessage());
        }
        return metrics;
    }

    public record TeamXgMetrics(double xG, double xGA, double ppda, double fieldTilt) {}
}
