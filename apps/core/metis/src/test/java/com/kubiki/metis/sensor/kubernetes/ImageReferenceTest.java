package com.kubiki.metis.sensor.kubernetes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageReferenceTest {

    @Test
    void parse_repositoryAndTag() {
        ImageReference ref = ImageReference.parse("weaveworksdemos/frontend:0.3.0");
        assertThat(ref.repository()).isEqualTo("weaveworksdemos/frontend");
        assertThat(ref.tag()).isEqualTo("0.3.0");
        assertThat(ref.fullReference()).isEqualTo("weaveworksdemos/frontend:0.3.0");
    }

    @Test
    void parse_defaultsToLatestWhenTagMissing() {
        ImageReference ref = ImageReference.parse("nginx");
        assertThat(ref.repository()).isEqualTo("nginx");
        assertThat(ref.tag()).isEqualTo("latest");
    }

    @Test
    void parse_stripsDigestSuffix() {
        ImageReference ref = ImageReference.parse("repo/app:1.0.0@sha256:abc123");
        assertThat(ref.repository()).isEqualTo("repo/app");
        assertThat(ref.tag()).isEqualTo("1.0.0");
    }

    @Test
    void parse_splitsRegistryHostFromRepositoryPath() {
        ImageReference ref = ImageReference.parse("docker.io/weaveworksdemos/frontend:0.3.0");
        assertThat(ref.registryHost()).isEqualTo("docker.io");
        assertThat(ref.repositoryPath()).isEqualTo("weaveworksdemos/frontend");
        assertThat(ref.tag()).isEqualTo("0.3.0");
    }

    @Test
    void parse_defaultsRegistryToDockerIoWhenHostAbsent() {
        ImageReference ref = ImageReference.parse("weaveworksdemos/frontend:0.3.0");
        assertThat(ref.registryHost()).isEqualTo("docker.io");
        assertThat(ref.repositoryPath()).isEqualTo("weaveworksdemos/frontend");
    }
}
