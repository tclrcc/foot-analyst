package com.tony.sportsAnalytics.model.dto;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardStats {
    private long totalMatches;
    private long finishedMatches;
    private double globalAccuracy; // % réussite global
    private double confidenceAccuracy; // % réussite quand proba > 60%
}
