package com.news.publish.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.entity.PlatformTask;
import com.news.publish.service.PlatformTaskFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlatformTaskFileStorageImpl implements PlatformTaskFileStorage {

    private final String DATA_FILE = "data/platform_tasks.json";
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final List<PlatformTask> cachedTasks = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public PlatformTaskFileStorageImpl() {
        loadFromFile();
    }

    private void loadFromFile() {
        lock.writeLock().lock();
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                List<PlatformTask> tasks = mapper.readValue(file, new TypeReference<List<PlatformTask>>() {});
                cachedTasks.clear();
                cachedTasks.addAll(tasks);
                long maxId = tasks.stream().mapToLong(t -> t.getId() != null ? t.getId() : 0).max().orElse(0);
                idGenerator.set(maxId + 1);
            } else {
                file.getParentFile().mkdirs();
                mapper.writeValue(file, new ArrayList<>());
            }
        } catch (Exception e) {
            log.error("Failed to load platform tasks from JSON", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveToFile() {
        try {
            File file = new File(DATA_FILE);
            file.getParentFile().mkdirs();
            mapper.writeValue(file, cachedTasks);
        } catch (Exception e) {
            log.error("Failed to save platform tasks to JSON", e);
        }
    }

    @Override
    public void deleteByPlatformKey(String platformKey) {
        lock.writeLock().lock();
        try {
            boolean removed = cachedTasks.removeIf(t -> platformKey.equals(t.getPlatformKey()));
            if (removed) {
                saveToFile();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public PlatformTask save(PlatformTask task) {
        lock.writeLock().lock();
        try {
            if (task.getId() == null) {
                task.setId(idGenerator.getAndIncrement());
                task.preUpdate();
                cachedTasks.add(task);
            } else {
                for (int i = 0; i < cachedTasks.size(); i++) {
                    if (cachedTasks.get(i).getId().equals(task.getId())) {
                        task.preUpdate();
                        cachedTasks.set(i, task);
                        break;
                    }
                }
            }
            saveToFile();
            return task;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<PlatformTask> findByPlatformKey(String platformKey) {
        lock.readLock().lock();
        try {
            return cachedTasks.stream()
                    .filter(t -> platformKey.equals(t.getPlatformKey()))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
}
