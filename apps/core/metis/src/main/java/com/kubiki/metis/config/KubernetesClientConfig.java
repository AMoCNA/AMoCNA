package com.kubiki.metis.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Produces the Fabric8 {@link KubernetesClient} bean used by all sensor implementations.
 *
 * <p>The client is only created when {@code metis.sensor.enabled=true}, so running
 * Metis without a Kubernetes cluster (e.g. in local dev or CI) does not require
 * a kubeconfig or in-cluster service account.
 *
 * <p>Configuration is resolved automatically by Fabric8 in this order:
 * <ol>
 *   <li>In-cluster service account ({@code /var/run/secrets/kubernetes.io/serviceaccount})</li>
 *   <li>{@code KUBECONFIG} environment variable</li>
 *   <li>{@code ~/.kube/config}</li>
 * </ol>
 */
@Configuration
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class KubernetesClientConfig {

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
