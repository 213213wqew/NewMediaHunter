package com.news.publish.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.entity.PublishLog;
import com.news.publish.service.PublishLogFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PublishLogFileStorageImpl implements PublishLogFileStorage {

    // 为了避免海量日志造成文件过大，我们仅在内存维护近期日志并序列化到文件。
    // 或采用每天一个日志文件。为了简单及性能，这里维持一个固定大小的记录队列写入 JSON。
    private final String LOG_DIR = "data/logs";
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private File getLogFile() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return new File(LOG_DIR, "publish_log_" + dateStr + ".json");
    }

    private List<PublishLog> loadFromFile(File file) {
        if (!file.exists()) return new ArrayList<>();
        try {
            return mapper.readValue(file, new TypeReference<List<PublishLog>>() {});
        } catch (Exception e) {
            log.error("Failed to load log file: {}", file.getName(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public PublishLog save(PublishLog logData) {
        lock.writeLock().lock();
        try {
            if (logData.getId() == null) {
                logData.setId(idGenerator.getAndIncrement());
                logData.onCreate();
            }
            File file = getLogFile();
            file.getParentFile().mkdirs();
            List<PublishLog> logs = loadFromFile(file);
            logs.add(logData);
            
            // Limit to last 5000 logs per file to avoid too memory overhead
            if (logs.size() > 5000) {
                logs = new ArrayList<>(logs.subList(logs.size() - 5000, logs.size()));
            }

            mapper.writeValue(file, logs);
            return logData;
        } catch (Exception e) {
            log.error("Failed to save publish log", e);
            return logData;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<PublishLog> findByTaskIdOrderByCreateTimeDesc(Long taskId) {
        lock.readLock().lock();
        try {
            // Read from today's log mostly, but if we need past logs, we would ideally read them.
            // For now, reading today's log file is sufficient for most recent checks.
            File file = getLogFile();
            List<PublishLog> logs = loadFromFile(file);
            
            List<PublishLog> result = logs.stream()
                .filter(l -> taskId.equals(l.getTaskId()))
                .sorted((a, b) -> {
                    if (a.getCreateTime() == null) return 1;
                    if (b.getCreateTime() == null) return -1;
                    return b.getCreateTime().compareTo(a.getCreateTime());
                })
                .collect(Collectors.toList());
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
}
