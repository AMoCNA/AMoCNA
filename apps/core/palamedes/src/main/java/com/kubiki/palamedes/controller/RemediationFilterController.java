package com.kubiki.palamedes.controller;

import com.kubiki.palamedes.service.RemediationFilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/filters")
@RequiredArgsConstructor
public class RemediationFilterController {

    private final RemediationFilterService filterService;

    @GetMapping("/intents")
    public Set<String> getAllowedIntents() {
        return filterService.getAllowedIntents();
    }

    @PostMapping("/intents")
    public String setAllowedIntents(@RequestBody Set<String> intents) {
        filterService.setAllowedIntents(intents);
        return "Filter updated. Allowed: " + intents;
    }

    @DeleteMapping("/intents")
    public String clearFilter() {
        filterService.clearFilter();
        return "Filter cleared. All remediations enabled.";
    }
}
