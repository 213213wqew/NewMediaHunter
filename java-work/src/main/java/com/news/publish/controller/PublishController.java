package com.news.publish.controller;

import com.news.publish.model.dto.PublishRequest;
import com.news.publish.model.dto.VideoBatchPublishRequest;
import com.news.publish.model.entity.PublishTask;
import com.news.publish.service.PublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/publish")
@RequiredArgsConstructor
public class PublishController {

    private final PublishService publishService;

    /**
     * 一键发布文章到多个账号
     */
    @PostMapping("/submit")
    public List<PublishTask> submitTask(@RequestBody PublishRequest request) {
        return publishService.submitPublishTask(request);
    }

    /**
     * 视频批量发布：多视频按账号轮询分配，按账号串行执行，最多 9 个账号并发。
     */
    @PostMapping("/submit-batch")
    public List<PublishTask> submitVideoBatch(@RequestBody VideoBatchPublishRequest request) {
        return publishService.submitVideoBatch(request);
    }

    @GetMapping("/tasks")
    public List<PublishTask> listTasks() {
        return publishService.getAllTasks();
    }

    @GetMapping("/tasks/{taskId}")
    public PublishTask getTask(@PathVariable Long taskId) {
        return publishService.getTaskById(taskId);
    }

    @GetMapping("/batch/{batchId}")
    public List<PublishTask> getBatchTasks(@PathVariable Long batchId) {
        return publishService.getTasksByBatchId(batchId);
    }

    @DeleteMapping("/tasks")
    public void deleteTasks(@RequestBody List<Long> taskIds) {
        publishService.deleteTasks(taskIds);
    }

    @GetMapping("/stats")
    public com.news.publish.model.dto.PublishStats getStats() {
        return publishService.getStatistics();
    }

    @GetMapping("/tasks/{taskId}/logs")
    public List<com.news.publish.model.entity.PublishLog> getTaskLogs(@PathVariable Long taskId) {
        return publishService.getLogsByTaskId(taskId);
    }
}
