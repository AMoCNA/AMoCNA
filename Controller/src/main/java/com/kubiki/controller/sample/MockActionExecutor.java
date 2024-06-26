package com.kubiki.controller.sample;

import com.kubiki.controller.commons.actions.dtos.ComplexActionArgs;
import com.kubiki.controller.commons.actions.dtos.MockActionArgs;
import com.kubiki.controller.commons.actions.dtos.NumberAndDelayActionArgs;
import com.kubiki.controller.commons.actions.dtos.infra.ChangePodCPUActionArgs;
import com.kubiki.controller.commons.annotations.complex.ComplexActionHandler;
import com.kubiki.controller.commons.annotations.simple.ActionCleanUpHandler;
import com.kubiki.controller.commons.annotations.simple.ActionFailureHandler;
import com.kubiki.controller.commons.annotations.simple.ActionHandler;
import com.kubiki.controller.commons.actions.dtos.MockRetryActionArgs;
import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import com.kubiki.controller.controllerlib.dto.ExecutionContext;
import com.kubiki.controller.controllerlib.service.complex.ComplexActionManager;
import com.kubiki.controller.sample.actions.ChangeApplicationThreadsNumberAction;
import com.kubiki.controller.sample.actions.MockAction2;
import com.kubiki.controller.sample.actions.MockRetryAction;
import com.kubiki.controller.sample.actions.infra.ChangePodCPUAction;
import com.kubiki.controller.sample.dto.ChangeApplicationThreadsNumberArgs;
import com.kubiki.controller.sample.dto.ScaleHeavyFlowActionArgs;
import com.kubiki.controller.sample.util.ActionCounterService;
import com.kubiki.controller.sample.dto.ChangeDataSourceArgs;
import com.kubiki.controller.sample.dto.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Log4j2
public class MockActionExecutor {

    private final ComplexActionManager complexActionManager;
    private final ActionCounterService actionCounterService;
    private Random random = new Random();

    //action 1 from doc
    @ActionHandler(action = "NumberAndDelayAction")
    public ExecutionContext performNumberAndDelayAction(NumberAndDelayActionArgs arg) {
        actionCounterService.increment();
        try {
            Thread.sleep(arg.getNumber() * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("performNumberAndDelayAction, actionCounter={}", actionCounterService.getCounter());
        return new ExecutionContext(true, null);
    }


    @ActionHandler(action = "MockAction2")
    public ExecutionContext performMockAction(MockActionArgs args) {
        // todo here we can call external service etc
        System.out.println(args.getTextToPrintFormApp());
        return new ExecutionContext(true, Map.of("reason", "2137"));
    }

    @ActionFailureHandler(action = "MockAction2")
    public void handleFailure(MockActionArgs args, ExecutionContext failureContext) {
        System.out.println(args.getTextToPrintFormApp());
        System.out.println(failureContext.getFailureContext());
    }

    @ActionHandler(action = "MockRetryAction")
    public ExecutionContext performMockRetryAction(MockRetryActionArgs args) {
        System.out.println(args.getTextToPrintFormApp());
        return new ExecutionContext(false, Map.of("reason", "2137"));
    }

    @ActionFailureHandler(action = "MockRetryAction")
    public void handleRetryFailure(MockRetryActionArgs args, ExecutionContext failureContext) {
        System.out.println(args.getTextToPrintFormApp());
        System.out.println(failureContext);
    }

    @ActionCleanUpHandler(action = "MockRetryAction")
    public void handleRetryCleanUp(MockRetryActionArgs args) {
        //context?
        System.out.println(args.getTextToPrintFormApp());
    }

    @ActionHandler(action = "ChangeDataSource")
    public ExecutionContext handleRetryCleanUp(ChangeDataSourceArgs args) {
        String subPath = args.getDataSource() == DataSource.NEW_SOURCE ? "use-new-db" : "use-old-db";
        String path = String.format("http://%s/utils/%s", args.getServiceUrl(), subPath);
        try {
            new RestTemplate().postForObject(path, null, Void.class);
        } catch (Exception ex) {
            return new ExecutionContext(false, Map.of("reason", ex.getMessage()));
        }
        return new ExecutionContext(true);
    }


    //complex action
    @ComplexActionHandler(action = "ComplexAction")
    public ExecutionContext handleComplexAction(ComplexActionArgs args) {
        complexActionManager.performBatchActions(
                new ActionScheduleRequest(new MockAction2(new MockActionArgs("mockActionArgs")), "idempotencyKey"),
                new ActionScheduleRequest(new MockRetryAction(new MockRetryActionArgs("mockRetryActionArgs"), 1))
        );
        return new ExecutionContext(false, Map.of("reasson", "complex failed"));
    }

    @ComplexActionHandler(action = "ScaleHeavyFlowAction")
    public ExecutionContext handleScaleHeavyFlowAction(ScaleHeavyFlowActionArgs args) {
        complexActionManager.performBatchActions(
                new ActionScheduleRequest(new ChangePodCPUAction(new ChangePodCPUActionArgs(
                        args.getDownstreamNamespace(),
                        args.getDownstreamPod(),
                        String.valueOf(args.getDownstreamCpu()),
                        String.valueOf(args.getDownstreamCpu())), 0, 120L)),
                new ActionScheduleRequest(new ChangeApplicationThreadsNumberAction(
                        new ChangeApplicationThreadsNumberArgs(args.getUpstreamThreads(), "http://" + args.getUpstreamService() + "/limited-threads/set-threads")))
        );
        return new ExecutionContext(true);
    }

    @ActionHandler(action = "ChangeApplicationThreadsNumberAction")
    public ExecutionContext handleChangeApplicationThreadsNumberAction(ChangeApplicationThreadsNumberArgs args) {
        try {
            new RestTemplate().postForObject(args.getUrl(), args, Void.class);
        } catch (Exception ex) {
            log.warn(ex.getMessage(), ex.getCause());
            return new ExecutionContext(false, Map.of("reason", ex.getMessage()));
        }
        log.info("ChangeApplicationThreadsNumberAction completed");
        return new ExecutionContext(true);
    }

    @ActionHandler(action = "FailingAction")
    public ExecutionContext handleFailingAction(Integer args) {
        if (random.nextInt(100) + 1 > args) {
            actionCounterService.incrementSuccessCounter();
            log.info("handleFailingAction [success], counter={}", actionCounterService.getSuccessCounter());
            return new ExecutionContext(true);
        }
        log.info("handleFailingAction [failure], maybe I will be retried :)");
        return new ExecutionContext(false);
    }
}