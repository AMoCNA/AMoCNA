package com.kubiki.controller.sample;

import com.kubiki.controller.commons.actions.dtos.ComplexActionArgs;
import com.kubiki.controller.commons.actions.dtos.MockActionArgs;
import com.kubiki.controller.commons.actions.dtos.NumberAndDelayActionArgs;
import com.kubiki.controller.sample.actions.*;
import com.kubiki.controller.commons.actions.dtos.MockRetryActionArgs;
import com.kubiki.controller.commons.definitons.ActionInvoker;
import com.kubiki.controller.commons.definitons.ActionResponseDto;
import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import com.kubiki.controller.sample.dto.ChangeDataSourceArgs;
import com.kubiki.controller.sample.util.ActionCounterService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/test")
@AllArgsConstructor
public class ExecutionsController {
    private final ActionInvoker actionInvoker;
    private final ActionCounterService actionCounterService;


    // test 1 z doca
    @PostMapping("test-1")
    public void test1(@RequestParam Integer arg, @RequestParam String idempotencyKey, @RequestParam long idempotencyWindow, @RequestParam int priority, @RequestParam int delay) {
        actionInvoker.invokeAction(new NumberAndDelayAction(new NumberAndDelayActionArgs(arg), idempotencyWindow), idempotencyKey, priority, delay);
    }


    //todo this is for poc only
    @PostMapping()
    public void test(@RequestParam String arg) {
        actionInvoker.invokeAction(new MockAction2(new MockActionArgs(arg)));
    }

    @PostMapping("idempotency")
    public ActionResponseDto testWithIdempotency(@RequestParam String arg, @RequestParam String idempotencyKey, @RequestParam int priority, @RequestParam int delay) {
        return actionInvoker.invokeAction(new MockAction2(new MockActionArgs(arg)), idempotencyKey, priority, delay);
    }

    @PostMapping("retry")
    public void retryTest(@RequestParam String arg, @RequestParam Integer retryNum) {
        actionInvoker.invokeAction(new MockRetryAction(new MockRetryActionArgs(arg), retryNum));
    }

    @PostMapping("kicacz")
    public ActionResponseDto sendRabbitRequest(@RequestParam String arg, @RequestParam String idempotencyKey, @RequestParam int priority, @RequestParam int delay) {
        return actionInvoker.invokeViaRabbit(new MockAction2(new MockActionArgs(arg)), idempotencyKey, priority, delay);
    }

    @PostMapping("test-2-m")
    public ActionResponseDto test2(@RequestBody ChangeDataSourceArgs args) {
        return actionInvoker.invokeAction(new ChangeDataSourceAction(args));
    }

    @Data
    static class TestDto{
        String idempotencyKey;
        int priority;
        int delay;
    }
    @PostMapping("kicacz-baczowski")
    public void sendRabbitRequestBatch(
            @RequestBody List<TestDto> requests) {
        List<ActionScheduleRequest> processedRequests = IntStream.range(0, requests.size()).mapToObj(i ->
                ActionScheduleRequest.builder()
                        .action(new MockAction2(new MockActionArgs(String.valueOf(i))))
                        .idempotencyKey(requests.get(i).idempotencyKey)
                        .delay(requests.get(i).delay)
                        .priority(requests.get(i).priority)
                        .build()).toList();
        actionInvoker.invokeBatchViaRabbit(processedRequests);
    }

    //complex action
    @PostMapping("complex")
    public void complexTest(@RequestParam String arg) {
        actionInvoker.invokeAction(new MockComplexAction(new ComplexActionArgs(arg)));
    }

    @GetMapping("success-counter")
    public int getAndResetSuccessCounter() {
        return actionCounterService.getAndClearSuccessCounter();
    }

    @GetMapping("counter")
    public int getAndResetCounter() {
        return actionCounterService.getAndClearCounter();
    }
}
