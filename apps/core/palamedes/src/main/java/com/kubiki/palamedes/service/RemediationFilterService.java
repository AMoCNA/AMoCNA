package com.kubiki.palamedes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
public class RemediationFilterService {
    private static final Logger log = LoggerFactory.getLogger(RemediationFilterService.class);
    private final Set<String> allowedIntents = Collections.synchronizedSet(new HashSet<>());

    public Set<String> getAllowedIntents() {
        synchronized (allowedIntents) {
            return new HashSet<>(allowedIntents);
        }
    }

    public void setAllowedIntents(Set<String> intents) {
        log.info("Updating allowed intents filter to: {}", intents);
        synchronized (allowedIntents) {
            allowedIntents.clear();
            if (intents != null) {
                allowedIntents.addAll(intents);
            }
        }
    }

    public void clearFilter() {
        log.info("Clearing allowed intents filter. All remediations are now enabled.");
        allowedIntents.clear();
    }

    public boolean isIntentAllowed(String intentLocalName) {
        synchronized (allowedIntents) {
            if (allowedIntents.isEmpty()) {
                return true;
            }
            return allowedIntents.contains(intentLocalName);
        }
    }
}
