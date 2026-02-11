import com.tony.sportsAnalytics.service.PredictionEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdvancedPredictionService {

    /**
     * Calcule la probabilité exacte d'un score (x,y) avec correction Dixon-Coles.
     */
    public double calculateProbability(int x, int y, double lambda, double mu, double rho) {
        double poissonProb = (poisson(x, lambda) * poisson(y, mu));
        double tau = 1.0;

        // Application de la correction Tau pour les scores faibles (0-0, 1-0, 0-1, 1-1)
        if (x == 0 && y == 0) tau = 1 - (lambda * mu * rho);
        else if (x == 0 && y == 1) tau = 1 + (lambda * rho);
        else if (x == 1 && y == 0) tau = 1 + (mu * rho);
        else if (x == 1 && y == 1) tau = 1 - rho;

        return Math.max(0, poissonProb * tau);
    }

    /**
     * Utilisation des xG pour stabiliser les lambdas.
     * L'xG est moins "bruyant" que le but réel pour prédire le futur.
     */
    private double calculateAdjustedLambda(PredictionEngineService.TeamPerformance perf, double opponentDefense, double homeAdvantage) {
        // Au lieu d'utiliser perf.goalsFor(), on utilise perf.avgXG()
        // lambda = alpha_i * beta_j * gamma
        return perf.attackRating() * opponentDefense * homeAdvantage;
    }

    private double poisson(int k, double lambda) {
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }
}
