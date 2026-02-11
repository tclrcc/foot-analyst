package com.tony.sportsAnalytics.service;

import jakarta.annotation.PostConstruct;
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
    private boolean isEnabled;

    public record WeatherCondition(double temperature, double windSpeed, boolean isRaining, String description) {}

    @PostConstruct
    public void init() {
        this.isEnabled = apiKey != null && !apiKey.trim().isEmpty();
        if (!isEnabled) {
            log.info("☁️ WeatherService désactivé (pas de clé API trouvée). La météo sera ignorée.");
        } else {
            log.info("☀️ WeatherService activé.");
        }
    }

    /**
     * Récupère la météo pour un match.
     */
    public Optional<WeatherCondition> getMatchWeather(Double lat, Double lon, String dateIso) {
        // 1. Si le service est désactivé ou coords manquantes, on sort SILENCIEUSEMENT
        if (!isEnabled || lat == null || lon == null) {
            return Optional.empty();
        }

        String url = String.format("https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&units=metric&appid=%s", lat, lon, apiKey);

        try {
            var response = restTemplate.getForObject(url, String.class);

            // Simulation parsing (à remplacer par Jackson plus tard)
            boolean isRaining = response != null && (response.contains("Rain") || response.contains("Drizzle"));
            double wind = 15.0;
            double temp = 12.0;

            return Optional.of(new WeatherCondition(temp, wind, isRaining, "Cloudy"));
        } catch (Exception e) {
            // On log en debug pour ne pas polluer la prod si l'API est down
            log.debug("Erreur API Météo : {}", e.getMessage());
            return Optional.empty();
        }
    }
}
