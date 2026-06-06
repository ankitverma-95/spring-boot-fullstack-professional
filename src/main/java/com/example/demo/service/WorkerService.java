package com.example.demo.service;

import com.example.demo.entity.Worker;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public Worker createWorker(Worker worker) {
        log.info("Creating new worker with phone: {}", worker.getPhone());
        return workerRepository.save(worker);
    }

    public Worker getWorkerById(Long id) {
        return workerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("WORKER_NOT_FOUND", "Worker not found with ID: " + id));
    }

    public List<Worker> getAllWorkers() {
        return workerRepository.findAll();
    }

    @Transactional
    public Worker updateWorker(Long id, Worker details) {
        log.info("Updating worker ID: {}", id);
        Worker worker = getWorkerById(id);
        
        worker.setName(details.getName());
        worker.setDesignation(details.getDesignation());
        worker.setDailyWageRate(details.getDailyWageRate());
        worker.setActive(details.getActive());
        
        Worker saved = workerRepository.save(worker);
        
        // Cache Invalidation
        invalidateWorkerCache(id);
        
        return saved;
    }

    private void invalidateWorkerCache(Long workerId) {
        try {
            String key = "active_worker:" + workerId;
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("Successfully invalidated Redis active worker cache for key: {}", key);
            }
        } catch (Exception e) {
            log.warn("Redis is offline. Skipped invalidating active worker cache for worker ID {}: {}", workerId, e.getMessage());
        }
    }
}
