package com.kubiki.controller.sample;

import com.kubiki.controller.commons.actions.dtos.infra.*;
import com.kubiki.controller.commons.annotations.complex.ComplexActionHandler;
import com.kubiki.controller.commons.annotations.simple.ActionCleanUpHandler;
import com.kubiki.controller.commons.annotations.simple.ActionFailureHandler;
import com.kubiki.controller.commons.annotations.simple.ActionHandler;
import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import com.kubiki.controller.controllerlib.dto.ExecutionContext;
import com.kubiki.controller.controllerlib.service.complex.ComplexActionManager;
import com.kubiki.controller.sample.actions.ChangePeriodAction;
import com.kubiki.controller.sample.actions.infra.ChangePodCPUAction;
import com.kubiki.controller.sample.actions.infra.CreatePVAction;
import com.kubiki.controller.sample.actions.infra.CreatePVCAction;
import com.kubiki.controller.sample.actions.infra.CreateStorageClassAction;
import com.kubiki.controller.sample.dto.ChangeCPUAndPeriodActionArgs;
import com.kubiki.controller.sample.dto.ChangePeriodActionArgs;
import com.kubiki.controller.sample.dto.ComplexCleanUpActionArgs;
import io.kubernetes.client.openapi.ApiException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


@Log4j2
@Service
@AllArgsConstructor
public class InfraActionExecutor {

    private InfraService infraService;
    private InfraServiceFabric infraServiceFabric;
    private final ComplexActionManager complexActionManager;

    @ActionHandler(action = "ResizeVolumeAction")
    public ExecutionContext performResizeVolumeAction(ResizeVolumeActionArgs args) {
        ExecutionContext executionContext;
        infraServiceFabric.resizePV(args.getPvName(), args.getNewSize());
        executionContext = new ExecutionContext(true, null);
        return executionContext;
    }

    @ActionFailureHandler(action = "ResizeVolumeAction")
    public void handleFailureResizeVolumeAction() {
        log.info("Failure handler Resize Volume");
    }


    //*************************************************//

    @ActionHandler(action = "CreatePVandPVCAction")
    public ExecutionContext performCreatePVandPVCAction(CreatePVandPVCActionArgs args) {
        ExecutionContext executionContext;
        infraServiceFabric.createPVandPVC(args.getNamespace(), args.getPersistentVolumeName(), args.getPersistentVolumeClaimName(), args.getStorageClassName(), args.getPvSize(), args.getPvcSize(), args.getHostPath());
        executionContext = new ExecutionContext(true, null);
        return executionContext;
    }

    @ActionFailureHandler(action = "CreatePVAndPVCAction")
    public void handleFailureCreateVolume() {
        log.info("Failure handler Create PV and PVC");
    }


    //****************************************************//

    @ActionHandler(action = "ChangePodCPUAction")
    public ExecutionContext performChangePodCPUAction(ChangePodCPUActionArgs args) {
        ExecutionContext executionContext;
        infraServiceFabric.changePodCPU(args.getNamespace(), args.getDeploymentName(), args.getNewLimits(), args.getNewRequests());
        executionContext = new ExecutionContext(true, null);
        return executionContext;
    }

    @ActionFailureHandler(action = "ChangePodCPUAction")
    public void handleFailureChangePodCPU() {
        log.info("Failure handler Change pod CPU");
    }


    //****************************************************//

    @ActionHandler(action = "ChangePodMemoryAction")
    public ExecutionContext performChangePodMemoryAction(ChangePodMemoryActionArgs args) {
        ExecutionContext executionContext;
        try {
            infraService.changePodMemory(args.getNamespace(), args.getPodName(), args.getNewMemorySize(), args.getMaxMemorySize());
            executionContext = new ExecutionContext(true, null);
        } catch (ApiException e) {
            log.info(e.getMessage());
            executionContext = new ExecutionContext(false, Map.of("error", e));
        }
        return executionContext;
    }

    @ActionFailureHandler(action = "ChangePodMemoryAction")
    public void handleFailureChangePodMemory(ExecutionContext failureContext) {
        log.info("Failure handler Change pod Memory: {}", failureContext.getFailureContext().get("error"));
    }


    //*********************** COMPLEX CLEAN UP *****************************//

    @ComplexActionHandler(action = "ComplexCleanUpAction")
    public ExecutionContext handleComplexCleanUpAction(ComplexCleanUpActionArgs args) {
        complexActionManager.performBatchActions(
                new ActionScheduleRequest(new CreateStorageClassAction(args.getCreateStorageClassActionArgs(), 0, 0)),
                new ActionScheduleRequest(new CreatePVAction(args.getCreatePVActionArgs(), 0, 0))
        );
        return new ExecutionContext(false, Map.of("reasson", "complex failed"));
    }

    @ActionHandler(action = "CreateStorageClassAction")
    public ExecutionContext performCreateStorageClassAction(CreateStorageClassActionArgs args) {
        ExecutionContext executionContext;
        infraServiceFabric.createStorageClass(args.getStorageClassName());
        executionContext = new ExecutionContext(true, null);
        return executionContext;
    }

    @ActionFailureHandler(action = "CreateStorageClassAction")
    public void handleFailureCreateStorageClassAction(ExecutionContext failureContext) {
        log.info("Failure handler CreateStorageClassAction: {}", failureContext.getFailureContext().get("error"));
    }

    @ActionCleanUpHandler(action = "CreateStorageClassAction")
    public void handleCleanUpCreateStorageClassAction(CreateStorageClassActionArgs args) {
        log.info("Clean up handler for ");
        infraServiceFabric.deleteStorageClass(args.getStorageClassName());
    }

    @ActionHandler(action = "CreatePVAction")
    public ExecutionContext performCreatePVAction(CreatePVActionArgs args) {
        ExecutionContext executionContext;

//        try {
//            infraService.createPV(args.getApiVersion(), args.getPersistentVolumeName(), args.getPvSize(), args.getStorageClassName(), args.getHostPath());
//            executionContext = new ExecutionContext(true, null);
//        } catch(ApiException e) {
//            log.info(e);
//        }
        executionContext = new ExecutionContext(false, Map.of("error", "Cannot create PV"));
        return executionContext;
    }

    @ActionFailureHandler(action = "CreatePVAction")
    public void handleFailureCreatePVAction(ExecutionContext failureContext) {
        log.info("Failure handler create PV: {}", failureContext.getFailureContext().get("error"));
    }

    @ActionHandler(action = "CreatePVCAction")
    public ExecutionContext performCreatePVCAction(CreatePVCActionArgs args) {
        ExecutionContext executionContext;
        try {
            infraService.createPVC(args.getNamespace(), args.getApiVersion(), args.getPersistentVolumeClaimName(), args.getPvcSize(), args.getStorageClassName());
            executionContext = new ExecutionContext(true, null);
        } catch(ApiException e) {
            log.info(e.getMessage());
            executionContext = new ExecutionContext(false, Map.of("error", e));
        }

        return executionContext;
    }

    @ActionFailureHandler(action = "CreatePVCAction")
    public void handleFailureCreatePVCAction(ExecutionContext failureContext) {
        log.info("Failure handler PVC: {}", failureContext.getFailureContext().get("error"));
    }


    @ComplexActionHandler(action = "CreateVolumeAction")
    public ExecutionContext complexCreateVolumeAction(ComplexCreateVolumeArgs args) {
        complexActionManager.performBatchActions(
                new ActionScheduleRequest(new CreateStorageClassAction(args.getStorageClassActionArgs(), args.getCreateStorageClassActionRetryNumber(), args.getCreateStorageClassActionIdempotencyWindow())),
                new ActionScheduleRequest(new CreatePVAction(args.getPvActionArgs(), args.getCreatePVActionRetryNumber(), args.getCreatePVActionIdempotencyWindow())),
                new ActionScheduleRequest(new CreatePVCAction(args.getPvcActionArgs(), args.getCreatePVCActionRetryNumber(), args.getCreatePVCActionIdempotencyWindow()))
                );
        return new ExecutionContext(false, Map.of("reasson", "complex failed"));
    }



    // TEST 3 //

    @ComplexActionHandler(action = "ChangeCPUAndPeriodAction")
    public ExecutionContext handleChangeCPUAndPeriodAction(ChangeCPUAndPeriodActionArgs args) {
        complexActionManager.performBatchActions(
                new ActionScheduleRequest(new ChangePeriodAction(args.getChangePeriodActionArgs(), 0, 120L)),
                new ActionScheduleRequest(new ChangePodCPUAction(args.getChangePodCPUActionArgs(), 0, 120L)
        ));
        return new ExecutionContext(true);
    }

    @ActionHandler(action = "ChangePeriodAction")
    public ExecutionContext handleChangePeriodAction(ChangePeriodActionArgs args) {
        try {
            String url = "http://" + args.getService() + "/config/change?frequency=" + args.getFrequency();
            new RestTemplate().put(url, Boolean.class);
            log.info("Frequency successfully changed.");
        } catch (Exception ex) {
            log.warn(ex.getMessage(), ex.getCause());
            return new ExecutionContext(false, Map.of("reason", ex.getMessage()));
        }
        return new ExecutionContext(true);
    }
}
