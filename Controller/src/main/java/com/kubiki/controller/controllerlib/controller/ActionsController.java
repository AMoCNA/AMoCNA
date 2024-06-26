package com.kubiki.controller.controllerlib.controller;

import com.kubiki.controller.commons.definitons.ActionResponseDto;
import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import com.kubiki.controller.controllerlib.service.ActionExecutionPlanner;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Controller
@RestController
@RequestMapping("/actions")
@AllArgsConstructor
public class ActionsController {

    private final ActionExecutionPlanner actionExecutionPlanner;

    @PostMapping("schedule")
    public ResponseEntity<?> perform(@RequestBody @Valid ActionScheduleRequest scheduleRequest) {
        ActionResponseDto actionResponseDto = actionExecutionPlanner.scheduleActionRequest(scheduleRequest);
        return new ResponseEntity<>(actionResponseDto, HttpStatus.OK);
    }

    @PostMapping("schedule-batch")
    public ResponseEntity<?> perform(@RequestBody @Valid List<ActionScheduleRequest> scheduleRequests) {
        scheduleRequests.forEach(r -> actionExecutionPlanner.scheduleActionRequest(r));
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
