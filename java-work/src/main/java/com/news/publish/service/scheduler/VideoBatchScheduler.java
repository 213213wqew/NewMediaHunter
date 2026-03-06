package com.news.publish.service.scheduler;

import com.news.publish.model.entity.PublishTask;
import com.news.publish.service.PublishTaskFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 视频批量发布调度器：按账号串行、全局最多 N 个账号并发。
 * 从待执行批次任务中按账号取「下一个」任务执行，任务结束后再调度下一批。
 */
@Slf4j
@Component
public class VideoBatchScheduler {

    private final PublishTaskFileStorage taskFileStorage;
    private final Executor executor;
    private final BatchTaskRunner taskRunner;

    @Value("${publish.batch.max-concurrent-accounts:9}")
    private int maxConcurrentAccounts = 9;

    private final Set<Long> runningAccountIds = ConcurrentHashMap.newKeySet();
    private final Object scheduleLock = new Object();

    public VideoBatchScheduler(
            PublishTaskFileStorage taskFileStorage,
            @Qualifier("taskExecutor") Executor taskExecutor,
            BatchTaskRunner taskRunner) {
        this.taskFileStorage = taskFileStorage;
        this.executor = taskExecutor;
        this.taskRunner = taskRunner;
    }

    /**
     * 尝试调度下一批任务：每个账号取一个待执行任务，最多填满 maxConcurrentAccounts 个槽位。
     * 单个任务执行完后会再次调用本方法，实现「该账号无剩余任务则结束、有则继续下一个」。
     */
    public void trySchedule() {
        synchronized (scheduleLock) {
            if (runningAccountIds.size() >= maxConcurrentAccounts) return;
            LocalDateTime now = LocalDateTime.now();
            List<PublishTask> pending = taskFileStorage.findPendingBatchTasks().stream()
                    .filter(t -> t.getScheduledTime() == null || !t.getScheduledTime().isAfter(now))
                    .collect(Collectors.toList());
            if (pending.isEmpty()) return;

            Map<Long, List<PublishTask>> byAccount = pending.stream()
                    .collect(Collectors.groupingBy(PublishTask::getAccountId));
            List<PublishTask> toRun = new ArrayList<>();
            for (Map.Entry<Long, List<PublishTask>> e : byAccount.entrySet()) {
                if (runningAccountIds.contains(e.getKey())) continue;
                List<PublishTask> list = e.getValue();
                list.sort(comparatorByBatchAndSequence());
                toRun.add(list.get(0));
                if (runningAccountIds.size() + toRun.size() >= maxConcurrentAccounts) break;
            }

            for (PublishTask t : toRun) {
                runningAccountIds.add(t.getAccountId());
                Long taskId = t.getId();
                Long accountId = t.getAccountId();
                executor.execute(() -> {
                    try {
                        taskRunner.run(taskId);
                    } finally {
                        runningAccountIds.remove(accountId);
                        trySchedule();
                    }
                });
            }
        }
    }

    private static Comparator<PublishTask> comparatorByBatchAndSequence() {
        return (a, b) -> {
            long ba = a.getBatchId() != null ? a.getBatchId() : 0L;
            long bb = b.getBatchId() != null ? b.getBatchId() : 0L;
            int c = Long.compare(ba, bb);
            if (c != 0) return c;
            
            int ai = a.getAccountSequenceIndex() != null ? a.getAccountSequenceIndex() : 0;
            int bi = b.getAccountSequenceIndex() != null ? b.getAccountSequenceIndex() : 0;
            if (ai != bi) return Integer.compare(ai, bi);
            
            // 兜底：按创建时间排序，如果没有批次信息的话
            LocalDateTime ta = a.getCreateTime() != null ? a.getCreateTime() : LocalDateTime.MIN;
            LocalDateTime tb = b.getCreateTime() != null ? b.getCreateTime() : LocalDateTime.MIN;
            return ta.compareTo(tb);
        };
    }
}
