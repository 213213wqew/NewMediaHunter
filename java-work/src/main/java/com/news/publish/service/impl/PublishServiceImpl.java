package com.news.publish.service.impl;

import com.news.publish.model.dto.PublishRequest;
import com.news.publish.model.dto.PublishStats;
import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.PublishTask;
import com.news.publish.repository.AccountRepository;
import com.news.publish.repository.ArticleRepository;
import com.news.publish.repository.PublishTaskRepository;
import com.news.publish.service.ComplianceService;
import com.news.publish.service.PublishService;
import com.news.publish.service.adapter.PlatformAdapter;
import com.news.publish.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishServiceImpl implements PublishService {

    private final ArticleRepository articleRepository;
    private final AccountRepository accountRepository;
    private final PublishTaskRepository taskRepository;
    private final com.news.publish.repository.PlatformRepository platformRepository;
    private final com.news.publish.repository.PublishLogRepository logRepository;
    private final List<PlatformAdapter> adapters;
    private final com.news.publish.service.MediaService mediaService;
    private final ComplianceService complianceService;

    private void recordLog(Long taskId, String level, String message, String request, String response, Integer status, Exception e) {
        com.news.publish.model.entity.PublishLog pl = new com.news.publish.model.entity.PublishLog();
        pl.setTaskId(taskId);
        pl.setLogLevel(level);
        pl.setMessage(message);
        pl.setRequestData(request);
        pl.setResponseData(response);
        pl.setHttpStatus(status);
        if (e != null) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            pl.setStackTrace(sw.toString());
        }
        logRepository.save(pl);
    }

    @Scheduled(fixedDelay = 60000)
    public void schedulePoller() {
        LocalDateTime now = LocalDateTime.now();
        List<PublishTask> dueTasks = taskRepository.findByPublishStatusAndScheduledTimeBefore(0, now);
        if (!dueTasks.isEmpty()) {
            log.info("定时分发轮询: 发现 {} 个到期任务", dueTasks.size());
            dueTasks.forEach(task -> executePublishTask(task.getId()));
        }
    }

    @Override
    @Transactional
    public List<PublishTask> submitPublishTask(PublishRequest request) {
        Article article = articleRepository.findById(request.getArticleId())
                .orElseThrow(() -> new RuntimeException("文章不存在"));

        if (!UserContext.isAdmin() && !article.getUserId().equals(UserContext.getUserId())) {
            throw new RuntimeException("无权操作此文章");
        }

        List<PublishTask> tasks = new ArrayList<>();
        for (Long accountId : request.getAccountIds()) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new RuntimeException("账号不存在"));
            if (!UserContext.isAdmin() && !account.getUserId().equals(UserContext.getUserId())) {
                throw new RuntimeException("无权操作此账号");
            }

            PublishTask task = new PublishTask();
            task.setArticleId(article.getId());
            task.setAccountId(accountId);
            task.setUserId(UserContext.getUserId());
            task.setPublishStatus(0); // 待处理
            task.setScheduledTime(request.getScheduledTime());
            tasks.add(taskRepository.save(task));
            
            if (request.getScheduledTime() == null || request.getScheduledTime().isBefore(LocalDateTime.now())) {
                executePublishTask(task.getId());
            }
        }
        return tasks;
    }

    @Async("taskExecutor")
    @Override
    public void executePublishTask(Long taskId) {
        PublishTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        try {
            task.setPublishStatus(2); // 发布中
            taskRepository.save(task);
            recordLog(taskId, "INFO", "开始执行任务分发流程", null, null, null, null);

            Article article = articleRepository.findById(task.getArticleId()).get();
            Account account = accountRepository.findById(task.getAccountId()).get();

            // 1. 合规检查
            List<String> complianceIssues = complianceService.checkContent(article);
            if (!complianceIssues.isEmpty()) {
                String errorMsg = "内容合规性检查不通过: " + String.join(", ", complianceIssues);
                recordLog(taskId, "ERROR", errorMsg, null, null, null, null);
                throw new RuntimeException(errorMsg);
            }
            recordLog(taskId, "INFO", "内容合规性检查通过", null, null, null, null);

            String platformKey = platformRepository.findById(account.getPlatformId())
                    .map(p -> p.getPlatformKey())
                    .orElseThrow(() -> new RuntimeException("未知平台协议"));

            PlatformAdapter adapter = adapters.stream()
                    .filter(a -> a.getPlatformKey().equals(platformKey))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("系统暂不支持该平台分发: " + platformKey));
            
            recordLog(taskId, "INFO", "匹配到平台适配器: " + platformKey, null, null, null, null);
            log.info("开始分发任务: 平台={}, 账号={}, 文章={}", 
                    platformKey, account.getAccountName(), article.getTitle());

            boolean isVideo = "video".equalsIgnoreCase(article.getContentType())
                    && article.getVideoUrl() != null && !article.getVideoUrl().isBlank();

            if (isVideo) {
                // 视频分发：直接调用各平台 publishVideo
                recordLog(taskId, "INFO", "检测到视频稿件，执行视频发布流程...", null, null, null, null);
                adapter.publishVideo(article, account, task, article.getVideoUrl());
            } else {
                // 2. 素材清洗与同步
                recordLog(taskId, "INFO", "开始进行素材清洗与多平台同步...", null, null, null, null);
                String cleanedContent = mediaService.cleanContent(article, account);
                recordLog(taskId, "INFO", "素材洗涤完成", null, null, null, null);

                // 3. 执行发布
                recordLog(taskId, "INFO", "正在连接 " + platformKey + " 接口...", null, null, null, null);
                adapter.publishArticle(article, account, task, cleanedContent);
            }

            task.setPublishStatus(3); // 成功
            task.setErrorMessage(null);
            taskRepository.save(task);
            recordLog(taskId, "INFO", "文章已成功同步至 " + platformKey + "！文章ID: " + task.getPlatformArticleId(), null, "SUCCESS", 200, null);

        } catch (Exception e) {
            log.error("分发任务执行异常: taskId={}", taskId, e);
            task.setPublishStatus(4); // 失败
            task.setErrorMessage(e.getMessage());
            taskRepository.save(task);
            recordLog(taskId, "ERROR", "分发任务执行失败: " + e.getMessage(), null, null, 500, e);
        }
    }

    @Override
    public List<PublishTask> getAllTasks() {
        if (UserContext.isAdmin()) {
            return taskRepository.findAll();
        }
        return taskRepository.findByUserId(UserContext.getUserId());
    }

    @Override
    public PublishStats getStatistics() {
        PublishStats stats = new PublishStats();
        long accountCount;
        long articleCount;
        List<PublishTask> allTasks;
        
        if (UserContext.isAdmin()) {
            accountCount = accountRepository.count();
            articleCount = articleRepository.count();
            allTasks = taskRepository.findAll();
        } else {
            Long userId = UserContext.getUserId();
            accountCount = accountRepository.findByUserId(userId).size();
            articleCount = articleRepository.findByUserId(userId).size();
            allTasks = taskRepository.findByUserId(userId);
        }
        
        stats.setTotalAccounts(accountCount);
        stats.setTotalArticles(articleCount);
        stats.setTotalTasks(allTasks.size());
        
        if (!allTasks.isEmpty()) {
            long successCount = allTasks.stream().filter(t -> t.getPublishStatus() == 3).count();
            stats.setSuccessRate((double) successCount / allTasks.size() * 100);
        } else {
            stats.setSuccessRate(0.0);
        }

        Map<String, Long> trendMap = new TreeMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            trendMap.put(LocalDate.now().minusDays(i).format(formatter), 0L);
        }
        
        allTasks.forEach(task -> {
            if (task.getCreateTime() != null) {
                String date = task.getCreateTime().format(formatter);
                if (trendMap.containsKey(date)) {
                    trendMap.put(date, trendMap.get(date) + 1);
                }
            }
        });
        
        List<PublishStats.ChartData> seriesData = new ArrayList<>();
        trendMap.forEach((k, v) -> seriesData.add(new PublishStats.ChartData(k, v)));
        stats.setSeriesData(seriesData);

        Map<Long, Long> platformCounts = allTasks.stream()
                .collect(Collectors.groupingBy(task -> {
                    return accountRepository.findById(task.getAccountId())
                            .map(Account::getPlatformId)
                            .orElse(0L);
                }, Collectors.counting()));

        List<PublishStats.ChartData> platformData = new ArrayList<>();
        platformCounts.forEach((pid, count) -> {
            String platformName = platformRepository.findById(pid)
                    .map(p -> p.getPlatformName())
                    .orElse("未知平台");
            platformData.add(new PublishStats.ChartData(platformName, count));
        });
        stats.setPlatformData(platformData);
        return stats;
    }

    @Override
    public List<com.news.publish.model.entity.PublishLog> getLogsByTaskId(Long taskId) {
        PublishTask task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("任务不存在"));
        if (!UserContext.isAdmin() && !task.getUserId().equals(UserContext.getUserId())) {
            throw new RuntimeException("无权查看此任务日志");
        }
        return logRepository.findByTaskIdOrderByCreateTimeDesc(taskId);
    }
}
