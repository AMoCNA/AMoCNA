package com.kubiki.controller.controllerlib.dto;

import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import lombok.Data;
import lombok.NonNull;

import java.util.Date;

@Data
public class QueuedActionScheduleRequest implements Comparable<QueuedActionScheduleRequest>{
    @NonNull
    private ActionScheduleRequest request;
    @NonNull
    private Date calculatedScheduleDate;

    public QueuedActionScheduleRequest(ActionScheduleRequest request) {
        this.request = request;
        this.calculatedScheduleDate = new Date(System.currentTimeMillis() + request.getDelay() * 1000L);
    }

    @Override
    public int compareTo(QueuedActionScheduleRequest other) {
        return calculatedScheduleDate.compareTo(other.calculatedScheduleDate);
    }
}
