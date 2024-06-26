package com.kubiki.controller.sample;

import com.kubiki.controller.commons.actions.dtos.infra.*;
import com.kubiki.controller.commons.definitons.ActionInvoker;
import com.kubiki.controller.commons.definitons.ActionResponseDto;
import com.kubiki.controller.sample.actions.infra.*;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/infra")
@AllArgsConstructor
public class InfraExecutionsController {
    private final ActionInvoker actionInvoker;

//    @PostMapping("resize-volume")
//    public ActionResponseDto sendResizeVolumeRequest(@RequestBody ResizeVolumeActionArgs args, @RequestParam String idempotencyKey, @RequestParam int priority, @RequestParam int delay, @RequestParam int retryNumber, @RequestParam int idempotencyWindow) {
//        return actionInvoker.invokeViaRabbit(new ResizeVolumeAction(args, retryNumber, idempotencyWindow), idempotencyKey, priority, delay);
//    }
//
//    @PostMapping("create-pv-and-pvc")
//    public ActionResponseDto sendCreatePVAndPVCRequest(@RequestBody CreatePVandPVCActionArgs args, @RequestParam String idempotencyKey, @RequestParam int priority, @RequestParam int delay, @RequestParam int retryNumber, @RequestParam int idempotencyWindow) {
//        return actionInvoker.invokeViaRabbit(new CreatePVandPVCAction(args, retryNumber, idempotencyWindow), idempotencyKey, priority, delay);
//    }
//
//    @PostMapping("change-pod-cpu")
//    public ActionResponseDto sendChangePodCPURequest(@RequestBody ChangePodCPUActionArgs args, @RequestParam String idempotencyKey, @RequestParam int priority, @RequestParam int delay, @RequestParam int retryNumber, @RequestParam int idempotencyWindow) {
//        return actionInvoker.invokeViaRabbit(new ChangePodCPUAction(args, retryNumber, idempotencyWindow), idempotencyKey, priority, delay);
//    }
//
//    @PostMapping("change-pod-memory")
//    public ActionResponseDto sendChangePodMemoryRequest(@RequestBody ChangePodMemoryActionArgs args, @RequestParam String idempotencyKey, @RequestParam int priority, @RequestParam int delay, @RequestParam int retryNumber, @RequestParam int idempotencyWindow) {
//        return actionInvoker.invokeViaRabbit(new ChangePodMemoryAction(args, retryNumber, idempotencyWindow), idempotencyKey, priority, delay);
//    }
//
//
//    /* COMPLEX */
//
//    @PostMapping("create-volume")
//    public ActionResponseDto sendCreateVolumeRequest(@RequestBody ComplexCreateVolumeArgs args, @RequestParam String idempotencyKey, @RequestParam int priority, @RequestParam int delay, @RequestParam int retryNumber, @RequestParam int idempotencyWindow) {
//        return actionInvoker.invokeViaRabbit(new ComplexCreateVolumeAction(args, retryNumber, idempotencyWindow), idempotencyKey, priority, delay);
//    }
}
