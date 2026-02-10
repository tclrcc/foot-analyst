package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.service.DataImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DataImportService importService;

    @PostMapping("/import/premier-league")
    public ResponseEntity<String> importPremierLeague() {
        String result = importService.importPremierLeagueData();
        return ResponseEntity.ok(result);
    }
}
