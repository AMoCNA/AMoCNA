package com.kubiki.controller.controllerlib.data.entity;

import com.kubiki.controller.commons.definitons.ActionState;

import java.util.Date;

public interface Action {

    Long getId();
    void setId(Long id);

    String getActionName();
    void setActionName(String actionName);

    Date getPerformTime();
    void setPerformTime(Date performTime);

    ActionState getState();
    void setState(ActionState actionState);

    Date getSuccessPerformTime();
    void setSuccessPerformTime(Date successPerformTime);

    Date getLastTryTime();
    void setLastTryTime(Date lastTryTime);

    String getIdempotencyId();
    void setIdempotencyId(String idempotencyId);

    Integer getPriority();
    void setPriority(Integer priority);

    String getParentId();
    void setParentId(String parentId);

    String getArguments();
    void setArguments(String arguments);

    Integer getSharingCounter();
    void setSharingCounter(Integer sharingCounter);
}
