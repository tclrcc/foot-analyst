package com.tony.sportsAnalytics.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

import static java.lang.Double.parseDouble;

@Service
@Slf4j
public class XgScraperService {

    public Map<String, TeamXgMetrics> scrapeAdvancedMetrics(String leagueUrl) {
        Map<String, TeamXgMetrics> metrics = new HashMap<>();
        try {
            // Utilisation de ".sslSocketFactory" pour ignorer les erreurs de certificat
            Document doc = Jsoup.connect(leagueUrl)
                    .sslSocketFactory(getUnsafeSslSocketFactory())
                    .timeout(10000) // Augmenter le timeout pour le scraping pro
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();

            Elements rows = doc.select("table#stats_squads_standard_for tbody tr");

            for (Element row : rows) {
                String teamName = row.select("th[data-stat=team]").text();
                if (teamName.isEmpty()) continue;

                double xG = parseDouble(row.select("td[data-stat=xg]").text());
                double xGA = parseDouble(row.select("td[data-stat=xg_against]").text());

                // Extraction des métriques tactiques
                double touchesInFinalThird = 0.0;
                Element touchesElem = row.selectFirst("td[data-stat=touches_att_3rd]");
                if (touchesElem != null) touchesInFinalThird = parseDouble(touchesElem.text());

                double fieldTilt = (touchesInFinalThird / 500.0) * 100.0;

                metrics.put(teamName, new TeamXgMetrics(xG, xGA, 10.5, fieldTilt));
            }
        } catch (Exception e) {
            log.error("Échec du scraping FBRef pour {}: {}", leagueUrl, e.getMessage());
        }
        return metrics;
    }

    /**
     * Crée une factory SSL qui fait confiance à tous les certificats (Utile pour le scraping).
     */
    private SSLSocketFactory getUnsafeSslSocketFactory() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new SecureRandom());
        return sc.getSocketFactory();
    }

    public record TeamXgMetrics(double xG, double xGA, double ppda, double fieldTilt) {}
}
