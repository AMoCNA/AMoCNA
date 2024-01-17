package com.example.kubernetes.management.controller;

import com.example.kubernetes.management.service.PodService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("kubernetes/management")
public class PodController {

    private final PodService podService;

    public PodController(PodService podService) {
        this.podService = podService;
    }

    @RequestMapping(value="/pod/delete", method = RequestMethod.GET)
    public HttpStatus deletePod(@RequestParam String namespace, @RequestParam String podName) {
        return podService.deletePod(namespace, podName);
    }
}
