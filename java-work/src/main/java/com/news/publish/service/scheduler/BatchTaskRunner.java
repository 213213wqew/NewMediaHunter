package com.news.publish.service.scheduler;

/**
 * 单任务同步执行器，供 {@link VideoBatchScheduler} 调用。
 * 实现方应阻塞直到该任务执行完毕（含技能调用的完整流程）。
 */
@FunctionalInterface
public interface BatchTaskRunner {
    void run(Long taskId);
}
