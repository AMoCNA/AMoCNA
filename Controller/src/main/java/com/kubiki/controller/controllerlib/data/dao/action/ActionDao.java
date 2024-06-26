package com.kubiki.controller.controllerlib.data.dao.action;

import com.kubiki.controller.controllerlib.data.entity.Action;

import java.util.Date;
import java.util.List;

public interface ActionDao {

    void create(Action action);
    Action findById(Long id);

    void update(Action action);

    void delete(Long id);

    List<Action> findByPerformTimeBeforeAndIdempotencyId(Date time, Long offsetSeconds, String idempotencyId, int priority);

    void lock(String key);

    void release(String key);

}
