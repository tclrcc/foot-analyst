package com.tony.sportsAnalytics.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

@Service
@Slf4j
public class XgScraperService {

    // Pseudo-Rate Limiting pour respecter FBRef (Evite le HTTP 429)
    private long lastRequestTime = 0;
    private static final long DELAY_BETWEEN_REQUESTS_MS = 3500;
    @Value("${scraper.api.key:}")
    private String scraperApiKey;

    public Map<String, TeamXgMetrics> scrapeAdvancedMetrics(String leagueUrl) {
        Map<String, TeamXgMetrics> metrics = new HashMap<>();

        try {
            log.info("🌐 Scraping via Proxy pour FBRef URL : {}", leagueUrl);

            // 1. On utilise HTTPS.
            // 2. On retire "render=true" (inutile et bloquant).
            // 3. On met "premium=true" pour utiliser les IP Résidentielles Anti-Cloudflare (coûte 10 crédits/requête, mais tu en as 5000, c'est très large).
            String proxyUrl = "https://api.scraperapi.com?api_key=" + scraperApiKey
                    + "&url=" + leagueUrl
                    + "&premium=true"
                    + "&render=true";

            Document doc = Jsoup.connect(proxyUrl)
                    .timeout(60000)
                    .ignoreHttpErrors(true)
                    .get();

            log.info("📄 Titre de la page reçue : {}", doc.title());

            // Si ScraperAPI renvoie une erreur textuelle au lieu du HTML de FBRef
            if (doc.text().contains("unauthorized") || doc.text().contains("forbidden")) {
                log.error("❌ Erreur ScraperAPI : {}", doc.text());
                return metrics;
            }

            // 1. Extraction de la table Standard (xG, xGA, Possession)
            Element standardTable = getTableFromDomOrComment(doc, "stats_squads_standard_for");
            if (standardTable != null) {
                extractStandardStats(standardTable, metrics);
            } else {
                log.warn("⚠️ Table 'Standard' introuvable dans le HTML renvoyé.");
            }

            // 2. Extraction de la table Possession (Touches in Final 3rd -> Field Tilt)
            Element possessionTable = getTableFromDomOrComment(doc, "stats_squads_possession_for");
            if (possessionTable != null) {
                extractPossessionStats(possessionTable, metrics);
            } else {
                log.warn("⚠️ Table 'Possession' introuvable dans le HTML renvoyé.");
            }

            // 3. Extraction de la table Passing (Passes Progressives)
            Element passingTable = getTableFromDomOrComment(doc, "stats_squads_passing_for");
            if (passingTable != null) {
                extractPassingStats(passingTable, metrics);
            } else {
                log.warn("⚠️ Table 'Passing' introuvable.");
            }

            log.info("✅ Scraping réussi : {} équipes récupérées.", metrics.size());

        } catch (Exception e) {
            log.error("❌ Échec critique du scraping FBRef pour {}: {}", leagueUrl, e.getMessage());
        }
        return metrics;
    }

    private void extractPassingStats(Element table, Map<String, TeamXgMetrics> metrics) {
        Elements rows = table.select("tbody tr");
        for (Element row : rows) {
            String teamName = row.select("th[data-stat=team]").text().trim();
            if (!metrics.containsKey(teamName)) continue; // On ne traite que les équipes déjà identifiées

            // FBRef: Colonne "prog_passes"
            double progPasses = parseDoubleSafe(row.select("td[data-stat=prog_passes]").text());

            // On met à jour le record (Immuable -> on recrée)
            metrics.computeIfPresent(teamName, (k, v) ->
                    new TeamXgMetrics(v.xG(), v.xGA(), v.ppda(), v.fieldTilt(), v.possession(), progPasses)
            );
        }
    }

    /**
     * Lit les xG et xGA depuis la table standard
     */
    private void extractStandardStats(Element table, Map<String, TeamXgMetrics> metrics) {
        Elements rows = table.select("tbody tr");
        for (Element row : rows) {
            String teamName = row.select("th[data-stat=team]").text().trim();
            if (teamName.isEmpty()) continue;

            double xG = parseDoubleSafe(row.select("td[data-stat=xg]").text());
            double xGA = parseDoubleSafe(row.select("td[data-stat=xg_against]").text());
            double possession = parseDoubleSafe(row.select("td[data-stat=possession]").text()); // NOUVEAU

            // On initialise l'objet avec la possession récupérée
            metrics.put(teamName, new TeamXgMetrics(xG, xGA, 10.5, 50.0, possession, 0.0));
        }
    }

    /**
     * Lit les "Touches in final third" depuis la table possession pour calculer le Field Tilt
     */
    private void extractPossessionStats(Element table, Map<String, TeamXgMetrics> metrics) {
        Elements rows = table.select("tbody tr");
        for (Element row : rows) {
            String teamName = row.select("th[data-stat=team]").text().trim();
            if (!metrics.containsKey(teamName)) continue;

            double touchesInFinalThird = parseDoubleSafe(row.select("td[data-stat=touches_att_3rd]").text());

            // Calcul basique du Field Tilt (à affiner si tu as les touches adverses)
            double fieldTilt = (touchesInFinalThird / 500.0) * 100.0;

            // Mise à jour de l'objet existant (Java Record étant immuable, on le recrée)
            metrics.computeIfPresent(teamName,
                    (k, existing) -> new TeamXgMetrics(existing.xG(), existing.xGA(), existing.ppda(), Math.min(100.0, fieldTilt), existing.possession(), existing.progressivePasses()));        }
    }

    /**
     * 🧠 MÉTHODE CLÉ : FBRef cache ses tables dans des commentaires HTML ()
     * Cette méthode cherche dans le DOM, puis dans les commentaires.
     */
    private Element getTableFromDomOrComment(Document doc, String tableId) {
        // 1. Recherche normale (DOM)
        Element table = doc.selectFirst("table#" + tableId);
        if (table != null) return table;

        // 2. Recherche dans les commentaires HTML
        for (Element element : doc.getAllElements()) {
            for (Node node : element.childNodes()) {
                if (node instanceof Comment) {
                    String commentData = ((Comment) node).getData();
                    if (commentData.contains("id=\"" + tableId + "\"")) {
                        // On parse le contenu du commentaire comme du HTML valide
                        Document hiddenDoc = Jsoup.parseBodyFragment(commentData);
                        return hiddenDoc.selectFirst("table#" + tableId);
                    }
                }
            }
        }
        return null;
    }

    private double parseDoubleSafe(String value) {
        try {
            if (value == null || value.trim().isEmpty()) return 0.0;
            return Double.parseDouble(value.replace(",", "."));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private synchronized void respectRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < DELAY_BETWEEN_REQUESTS_MS) {
            Thread.sleep(DELAY_BETWEEN_REQUESTS_MS - elapsed);
        }
        lastRequestTime = System.currentTimeMillis();
    }

    private SSLSocketFactory getUnsafeSslSocketFactory() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());
        return sc.getSocketFactory();
    }

    public record TeamXgMetrics(double xG, double xGA, double ppda, double fieldTilt, double possession, double progressivePasses) {}
}
