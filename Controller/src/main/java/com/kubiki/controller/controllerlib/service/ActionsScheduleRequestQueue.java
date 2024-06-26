package com.kubiki.controller.controllerlib.service;

import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import com.kubiki.controller.controllerlib.dto.QueuedActionScheduleRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;

@Service
@RequiredArgsConstructor
@Log4j2
public class ActionsScheduleRequestQueue {
    private final ActionService actionService;
    private final PriorityBlockingQueue<QueuedActionScheduleRequest> queue = new PriorityBlockingQueue<>();

    @PostConstruct
    private void scheduleAsyncPollingTask() {
        CompletableFuture.runAsync(this::poolQueue);
    }

    private void poolQueue(){
        while (true) {
            try {
                QueuedActionScheduleRequest queuedRequest = queue.peek();
                if (Objects.nonNull(queuedRequest) && !queuedRequest.getCalculatedScheduleDate().after(new Date())) {
                    queue.take();
                    log.info("Time for action to be executed. idempotencyKey: {} desired time: {} actual time {}:",
                            queuedRequest.getRequest().getIdempotencyKey(), queuedRequest.getCalculatedScheduleDate(), new Date());
                    actionService.handleActionRequest(queuedRequest.getRequest());
                } else {
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                log.warn("An Exception occurred while polling actions schedule request queue: {}. " +
                        "The pooling will be resumed", e.getMessage());
            }
        }
    }

    public void put(ActionScheduleRequest scheduleRequest) {
        queue.put(new QueuedActionScheduleRequest(scheduleRequest));
    }
}
