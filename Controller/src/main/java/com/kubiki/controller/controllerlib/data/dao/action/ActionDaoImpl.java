package com.kubiki.controller.controllerlib.data.dao.action;

import com.kubiki.controller.controllerlib.data.entity.Action;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;


@Repository
public class ActionDaoImpl implements ActionDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void create(Action action) {
        try {
            entityManager.persist(action);
            entityManager.refresh(action);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    @Deprecated
    public void lock(String key) {
        entityManager.createNativeQuery(String.format("SELECT GET_LOCK('%s',30);", key)).getResultList();
    }

    @Override
    @Deprecated
    public void release(String key) {
        entityManager.createNativeQuery(String.format("SELECT RELEASE_LOCK('%s');", key)).getResultList();
    }

    @Transactional
    @Override
    public Action findById(Long id) {
        String jpqlQuery = "SELECT e FROM ActionEntity e WHERE e.id = :id";
        Query query = entityManager.createQuery(jpqlQuery, Action.class);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        query.setParameter("id", id);

        List<Action> list = query.getResultList();
        return !list.isEmpty() ? list.get(0) : null;
    }

    @Transactional
    @Override
    public void update(Action action) {
        entityManager.merge(action);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        Action action = entityManager.find(Action.class, id);
        if(action != null) {
            entityManager.remove(action);
        } else {
            throw new EntityNotFoundException("Entity not found for id: " + id);
        }
    }

    @Override
    public List<Action> findByPerformTimeBeforeAndIdempotencyId(Date time, Long offsetSeconds, String idempotencyId, int priority) {
        String jpqlQuery = "SELECT e FROM ActionEntity e WHERE e.performTime >= :startTime and e.state <> 'FAILED' and e.state <> 'ROLLED_BACK' and e.idempotencyId = :idempotencyId and e.priority >= :priority and e.sharingCounter >= 1 ORDER BY e.id DESC";

        // Calculate start and end times based on the provided time and offset
        Date startTime = new Date(time.getTime() - (offsetSeconds * 1000));  // Subtract offset in milliseconds

        entityManager.clear();
        Query query = entityManager.createQuery(jpqlQuery, Action.class);
        query.setParameter("startTime", startTime);
        query.setParameter("idempotencyId", idempotencyId);
        query.setParameter("priority", priority);
        return query.getResultList();
    }
}
