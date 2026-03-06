package com.news.publish.service.scheduler;

import com.news.publish.service.PublishService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 将 {@link PublishService#runTaskSync(Long)} 适配为 {@link BatchTaskRunner}，供 {@link VideoBatchScheduler} 使用。
 * 使用 {@link Lazy} 注入 PublishService 以打破与 PublishServiceImpl 的循环依赖。
 */
@Component
public class PublishTaskRunnerBean implements BatchTaskRunner {

    private final PublishService publishService;

    public PublishTaskRunnerBean(@Lazy PublishService publishService) {
        this.publishService = publishService;
    }

    @Override
    public void run(Long taskId) {
        publishService.runTaskSync(taskId);
    }
}
