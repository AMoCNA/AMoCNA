package com.example.droolsprototype.execution;

import com.example.droolsprototype.demo.tests.utils.CSVUtils;
import com.example.droolsprototype.services.KubernetesManagementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TestExecutionService extends ExecutionService {

    @Value("${test.save.path}")
    private String savePath;

    public TestExecutionService(KubernetesManagementService kubernetesManagementService) {
        super(kubernetesManagementService);
    }

    private long startTime;
    private long postQueryTime;
    private long postShuffleTime;
    private long timeAfterParse;
    private int metricsNum;

    private AtomicInteger counter = new AtomicInteger(0);

    public void execute(String message, String message2){
        this.counter.addAndGet(-1);
        if (this.counter.get() == 0) {
            var time = System.currentTimeMillis();
            CSVUtils.saveToCsv(savePath, List.of(metricsNum, postQueryTime - startTime, timeAfterParse - postQueryTime ,time - postShuffleTime));
        }
    }

    public void insertTimes(int metricsNum, long startTime, long postQueryTime, long postShuffleTime, long timeAfterParse) {
        if (this.counter.get() != 0) {
            CSVUtils.saveToCsv(savePath, List.of("=======ERROR======="));
        }
        this.counter.set(10);
        this.metricsNum = metricsNum;
        this.startTime = startTime;
        this.postQueryTime = postQueryTime;
        this.postShuffleTime = postShuffleTime;
        this.timeAfterParse = timeAfterParse;
    }
}
