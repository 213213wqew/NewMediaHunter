package com.news.publish.service;

import com.news.publish.model.dto.PublishRequest;
import com.news.publish.model.dto.VideoBatchPublishRequest;
import com.news.publish.model.entity.PublishTask;
import java.util.List;

/**
 * 文章发布业务接口
 */
public interface PublishService {

    /**
     * 提交发布任务 (一键发布)
     * @return 返回创建的任务列表
     */
    List<PublishTask> submitPublishTask(PublishRequest request);

    /**
     * 视频批量发布：多视频按账号轮询分配，按账号串行执行，最多 9 个账号并发。
     * @return 本批创建的任务列表
     */
    List<PublishTask> submitVideoBatch(VideoBatchPublishRequest request);

    /**
     * 异步执行单条发布任务 (供外部触发重试等)
     */
    void executePublishTask(Long taskId);

    /**
     * 同步执行单条任务，供批量调度器回调，阻塞直到执行完毕。
     */
    void runTaskSync(Long taskId);
    List<PublishTask> getAllTasks();
    com.news.publish.model.dto.PublishStats getStatistics();
    List<com.news.publish.model.entity.PublishLog> getLogsByTaskId(Long taskId);
    PublishTask getTaskById(Long taskId);

    /** 定时轮询到期任务并执行（由 @Scheduled 调用，需在接口声明以便代理可调用） */
    void schedulePoller();
}
