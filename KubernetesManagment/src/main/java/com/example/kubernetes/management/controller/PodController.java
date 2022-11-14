package com.example.kubernetes.management.controller;

import com.example.kubernetes.management.service.PodService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("kubernetes/management")
public class PodController {

    private final PodService podService;

    public PodController(PodService podService) {
        this.podService = podService;
    }

    @RequestMapping(value="/pod/delete", method = RequestMethod.POST)
    public ResponseEntity deletePod(@RequestParam String namespace, @RequestParam String podName) {
        return podService.deletePod(namespace, podName);
    }
}
