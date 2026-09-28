package com.kubiki.palamedes.scig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads Syft SBOMs produced by the SCIG CronJob from Redis.
 *
 * <p>Keys (see {@code infra/scig/scan.sh}):
 * <ul>
 *   <li>{@code sbom:repo:{repository}:{tag}} — Syft JSON</li>
 *   <li>{@code sbom:meta:{repository}:{tag}} — scan metadata JSON</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "palamedes.scig.enabled", havingValue = "true")
public class SbomRedisClient {

    private static final Logger log = LoggerFactory.getLogger(SbomRedisClient.class);

    public static final String REPO_KEY_PREFIX = "sbom:repo:";
    public static final String META_KEY_PREFIX = "sbom:meta:";

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public SbomRedisClient(
            StringRedisTemplate redis,
            @Value("${palamedes.scig.redis-key-prefix:}") String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }

    public Optional<String> getSbomJson(String repository, String tag) {
        String key = repoKey(repository, tag);
        String value = redis.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            log.debug("No SBOM in Redis for key {}", key);
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public Optional<String> getMetaJson(String repository, String tag) {
        String key = metaKey(repository, tag);
        return Optional.ofNullable(redis.opsForValue().get(key)).filter(v -> !v.isBlank());
    }

    /**
     * Lists {@code repository:tag} pairs for which an SBOM exists (from meta keys).
     */
    public List<ImageRef> listScannedImages() {
        Set<String> keys = redis.keys(keyPrefix + META_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<ImageRef> images = new ArrayList<>();
        String strip = keyPrefix + META_KEY_PREFIX;
        for (String key : keys) {
            if (!key.startsWith(strip)) {
                continue;
            }
            String rest = key.substring(strip.length());
            int sep = rest.lastIndexOf(':');
            if (sep <= 0 || sep == rest.length() - 1) {
                log.warn("Skipping malformed SBOM meta key: {}", key);
                continue;
            }
            images.add(new ImageRef(rest.substring(0, sep), rest.substring(sep + 1)));
        }
        return List.copyOf(images);
    }

    public String repoKey(String repository, String tag) {
        return keyPrefix + REPO_KEY_PREFIX + repository + ":" + tag;
    }

    public String metaKey(String repository, String tag) {
        return keyPrefix + META_KEY_PREFIX + repository + ":" + tag;
    }

    public record ImageRef(String repository, String tag) {
        public String imageRef() {
            return repository + ":" + tag;
        }
    }
}
