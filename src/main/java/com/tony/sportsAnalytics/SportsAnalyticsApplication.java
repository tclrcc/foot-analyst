package com.tony.sportsAnalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SportsAnalyticsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SportsAnalyticsApplication.class, args);
	}

}
