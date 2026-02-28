package com.news.publish.controller;

import com.news.publish.model.dto.PublishRequest;
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

    @GetMapping("/tasks")
    public List<PublishTask> listTasks() {
        return publishService.getAllTasks();
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
