package com.news.publish.service;

import com.news.publish.model.dto.PublishRequest;
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
     * 执行具体的发布逻辑 (供调度引擎调用)
     */
    void executePublishTask(Long taskId);
    List<PublishTask> getAllTasks();
    com.news.publish.model.dto.PublishStats getStatistics();
    List<com.news.publish.model.entity.PublishLog> getLogsByTaskId(Long taskId);

    /** 定时轮询到期任务并执行（由 @Scheduled 调用，需在接口声明以便代理可调用） */
    void schedulePoller();
}
