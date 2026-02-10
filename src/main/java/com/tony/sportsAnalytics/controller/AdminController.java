package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.service.DataImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DataImportService importService;

    @PostMapping("/import/premier-league")
    public ResponseEntity<String> importPremierLeague(@RequestParam(defaultValue = "false") boolean forceUpdate) {
        // On passe le paramètre au service
        String result = importService.importPremierLeagueData(forceUpdate);
        return ResponseEntity.ok(result);
    }
}
