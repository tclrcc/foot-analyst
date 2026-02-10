// Fichier: src/main/java/com/tony/sportsAnalytics/service/WeatherService.java
package com.tony.sportsAnalytics.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;

@Service
@Slf4j
public class WeatherService {

    @Value("${weather.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public record WeatherCondition(double temperature, double windSpeed, boolean isRaining, String description) {}

    /**
     * Récupère la météo pour un match.
     * Utilise un Try-Catch pour ne pas bloquer l'analyse si l'API météo échoue.
     */
    public Optional<WeatherCondition> getMatchWeather(Double lat, Double lon, String dateIso) {
        // Si pas de coordonnées OU pas de clé API configurée, on renvoie vide sans crasher
        if (lat == null || lon == null || apiKey == null || apiKey.isEmpty()) {
            log.warn("⚠️ Météo ignorée : Coordonnées manquantes ou Clé API non configurée.");
            return Optional.empty();
        }

        // Note: Pour un match futur (> 5 jours), les API gratuites sont limitées.
        // Ici on simule un appel "Current Weather" pour l'exemple, ou "Forecast".
        String url = String.format("https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&units=metric&appid=%s", lat, lon, apiKey);

        try {
            // Mapping JSON brut (simplifié pour l'exemple)
            // Dans un vrai projet, créez des classes DTO pour la réponse OpenWeather
            var response = restTemplate.getForObject(url, String.class);

            // Simulation du parsing (à remplacer par un vrai parsing JSON avec Jackson)
            // C'est juste pour illustrer la logique métier
            boolean isRaining = response != null && response.contains("Rain");
            double wind = 15.0; // Valeur extraite du JSON
            double temp = 12.0; // Valeur extraite du JSON

            return Optional.of(new WeatherCondition(temp, wind, isRaining, "Cloudy"));
        } catch (Exception e) {
            log.warn("⚠️ Impossible de récupérer la météo : {}", e.getMessage());
            return Optional.empty();
        }
    }
}
