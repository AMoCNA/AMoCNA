package com.example.droolsprototype.execution;

import com.example.droolsprototype.services.KubernetesManagementService;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Class simulating this service communicating with executor, who makes changes in the system
 */
@Service
@RestController
public class ExecutionService {

    private final KubernetesManagementService kubernetesManagementService;

    public ExecutionService(KubernetesManagementService kubernetesManagementService) {
        this.kubernetesManagementService = kubernetesManagementService;
    }
    public void log(String message){
        System.out.println("Executor: " + message);
    }

    public void deletePod(String namespace, String podName) {
        this.kubernetesManagementService.deletePod(namespace, podName);
    }
}
