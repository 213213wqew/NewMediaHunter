package com.news.publish.service.impl;

import com.news.publish.model.dto.PublishRequest;
import com.news.publish.model.dto.PublishStats;
import com.news.publish.model.dto.VideoBatchPublishRequest;
import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.PublishTask;
import com.news.publish.service.AccountService;
import com.news.publish.service.ArticleFileStorage;
import com.news.publish.service.PublishTaskFileStorage;
import com.news.publish.service.ComplianceService;
import com.news.publish.service.MediaService;
import com.news.publish.service.PublishService;
import com.news.publish.service.adapter.PlatformAdapter;
import com.news.publish.service.scheduler.VideoBatchScheduler;
import com.news.publish.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.Executor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishServiceImpl implements PublishService {

    private final ArticleFileStorage articleFileStorage;
    private final AccountService accountService;
    private final PublishTaskFileStorage taskFileStorage;
    private final com.news.publish.service.PlatformService platformService;
    private final com.news.publish.service.PublishLogFileStorage logRepository;
    private final List<PlatformAdapter> adapters;
    private final com.news.publish.service.MediaService mediaService;
    private final com.news.publish.service.MediaResourceFileStorage mediaResourceFileStorage;
    private final ComplianceService complianceService;
    private final Executor taskExecutor;
    private final VideoBatchScheduler videoBatchScheduler;

    private void recordLog(Long taskId, String level, String message, String request, String response, Integer status, Exception e) {
        try {
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
        } catch (Exception ex) {
            log.warn("写入发布日志失败（可能未使用数据库）: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void schedulePoller() {
        LocalDateTime now = LocalDateTime.now();
        List<PublishTask> dueTasks = taskFileStorage.findByPublishStatusAndScheduledTimeBefore(0, now);
        if (dueTasks.isEmpty()) return;
        boolean hasBatch = dueTasks.stream().anyMatch(t -> t.getBatchId() != null);
        if (hasBatch) {
            videoBatchScheduler.trySchedule();
        } else {
            log.info("定时分发轮询: 发现 {} 个到期任务", dueTasks.size());
            dueTasks.forEach(task -> taskExecutor.execute(() -> doExecutePublishTask(task.getId())));
        }
    }

    @Override
    public List<PublishTask> submitPublishTask(PublishRequest request) {
        Article article = articleFileStorage.findById(request.getArticleId())
                .orElseThrow(() -> new RuntimeException("文章不存在"));

        if (!UserContext.isAdmin() && !article.getUserId().equals(UserContext.getUserId())) {
            throw new RuntimeException("无权操作此文章");
        }

        List<PublishTask> tasks = new ArrayList<>();
        Long batchId = System.currentTimeMillis(); // 赋予批次 ID，使其进入受控并行调度
        for (Long accountId : request.getAccountIds()) {
            Account account = accountService.getById(accountId)
                    .orElseThrow(() -> new RuntimeException("账号不存在"));
            if (!UserContext.isAdmin() && account.getUserId() != null && !account.getUserId().equals(UserContext.getUserId())) {
                throw new RuntimeException("无权操作此账号");
            }

            PublishTask task = new PublishTask();
            task.setArticleId(article.getId());
            task.setAccountId(accountId);
            task.setUserId(UserContext.getUserId());
            task.setPublishStatus(0); // 待处理
            task.setScheduledTime(request.getScheduledTime());
            task.setBatchId(batchId); // 关联批次
            tasks.add(taskFileStorage.save(task));
        }

        // 所有任务持久化完成后，统一触发一次调度，确保调度器能看见所有待处理任务
        if (request.getScheduledTime() == null || request.getScheduledTime().isBefore(LocalDateTime.now())) {
            videoBatchScheduler.trySchedule();
        }
        return tasks;
    }

    @Override
    public List<PublishTask> submitVideoBatch(VideoBatchPublishRequest request) {
        List<Long> articleIds = request.getArticleIds() != null ? request.getArticleIds() : List.of();
        List<Long> accountIds = request.getAccountIds() != null ? request.getAccountIds() : List.of();
        if (articleIds.isEmpty() || accountIds.isEmpty()) {
            throw new RuntimeException("请提供至少一个文章ID和至少一个账号ID");
        }
        int n = articleIds.size();
        int m = accountIds.size();
        Long batchId = System.currentTimeMillis();
        List<PublishTask> tasks = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Long articleId = articleIds.get(i);
            Article article = articleFileStorage.findById(articleId)
                    .orElseThrow(() -> new RuntimeException("文章不存在: " + articleId));
            if (!UserContext.isAdmin() && !article.getUserId().equals(UserContext.getUserId())) {
                throw new RuntimeException("无权操作文章: " + articleId);
            }
            int accountIndex = i % m;
            Long accountId = accountIds.get(accountIndex);
            Account account = accountService.getById(accountId)
                    .orElseThrow(() -> new RuntimeException("账号不存在: " + accountId));
            if (!UserContext.isAdmin() && account.getUserId() != null && !account.getUserId().equals(UserContext.getUserId())) {
                throw new RuntimeException("无权操作账号: " + accountId);
            }
            int accountSequenceIndex = i / m;

            PublishTask task = new PublishTask();
            task.setArticleId(articleId);
            task.setAccountId(accountId);
            task.setUserId(UserContext.getUserId());
            task.setPublishStatus(0);
            task.setScheduledTime(request.getScheduledTime());
            task.setBatchId(batchId);
            task.setAccountSequenceIndex(accountSequenceIndex);
            tasks.add(taskFileStorage.save(task));
        }

        if (request.getScheduledTime() == null || !request.getScheduledTime().isAfter(LocalDateTime.now())) {
            videoBatchScheduler.trySchedule();
        }
        return tasks;
    }

    @Override
    public void executePublishTask(Long taskId) {
        videoBatchScheduler.trySchedule();
    }

    @Override
    public void runTaskSync(Long taskId) {
        doExecutePublishTask(taskId);
    }

    /** 实际执行发布逻辑，由线程池或批量调度器调用 */
    private void doExecutePublishTask(Long taskId) {
        PublishTask task = taskFileStorage.findById(taskId).orElse(null);
        if (task == null) return;

        try {
            task.setPublishStatus(2); // 发布中
            taskFileStorage.save(task);
            recordLog(taskId, "INFO", "开始执行任务分发流程", null, null, null, null);

            Article article = articleFileStorage.findById(task.getArticleId())
                    .orElseThrow(() -> new RuntimeException("文章不存在"));
            Account account = accountService.getById(task.getAccountId()).orElseThrow(() -> new RuntimeException("账号不存在"));

            // 1. 合规检查
            List<String> complianceIssues = complianceService.checkContent(article);
            if (!complianceIssues.isEmpty()) {
                String errorMsg = "内容合规性检查不通过: " + String.join(", ", complianceIssues);
                recordLog(taskId, "ERROR", errorMsg, null, null, null, null);
                throw new RuntimeException(errorMsg);
            }
            recordLog(taskId, "INFO", "内容合规性检查通过", null, null, null, null);

            String platformKey = platformService.getPlatformById(account.getPlatformId()) != null ? platformService.getPlatformById(account.getPlatformId()).getPlatformKey() : null;
            if (platformKey == null) {
                throw new RuntimeException("未知平台协议");
            }

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
                
                // 将本地化后的 HTML 回写到文章，这样前端下次加载时就能看到本地 URL
                if (cleanedContent != null && !cleanedContent.equals(article.getContent())) {
                    article.setContent(cleanedContent);
                    // 同时更新 platformSettings 中的封面图 URL，避免封面图仍然指向旧外链
                    if (article.getPlatformSettings() != null && !article.getPlatformSettings().isBlank()) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            java.util.Map<String, Object> settings = mapper.readValue(article.getPlatformSettings(), java.util.Map.class);
                            java.util.Map<String, Object> bjhSettings = (java.util.Map<String, Object>) settings.get("baijiahao");
                            if (bjhSettings != null) {
                                String coverImage = (String) bjhSettings.get("coverImage");
                                if (coverImage != null && (coverImage.startsWith("http://") || coverImage.startsWith("https://"))) {
                                    // 查 MediaResource 表找封面图的本地副本
                                    mediaResourceFileStorage.findByOriginalUrl(coverImage).ifPresent(r -> {
                                        bjhSettings.put("coverImage", r.getPlatformMediaUrl());
                                        log.info("封面图 URL 已自动同步为本地: {}", r.getPlatformMediaUrl());
                                    });
                                    settings.put("baijiahao", bjhSettings);
                                    article.setPlatformSettings(mapper.writeValueAsString(settings));
                                }
                            }
                        } catch (Exception ex) {
                            log.warn("同步封面图 URL 失败: {}", ex.getMessage());
                        }
                    }
                    articleFileStorage.save(article);
                    log.info("文章图片已本地化并回写到数据库");
                }

                // 3. 执行发布
                recordLog(taskId, "INFO", "正在连接 " + platformKey + " 接口...", null, null, null, null);
                adapter.publishArticle(article, account, task, cleanedContent);
            }

            // 无论技能内是否点击最终发布，只要整体流程成功执行，就记为成功（前端需看到「已完成」）
            task.setPublishStatus(3); // 成功
            task.setErrorMessage(null);
            taskFileStorage.save(task);
            recordLog(taskId, "INFO", "文章已成功同步至 " + platformKey + "！文章ID: " + task.getPlatformArticleId(), null, "SUCCESS", 200, null);

        } catch (Exception e) {
            log.error("分发任务执行异常: taskId={}", taskId, e);
            task.setPublishStatus(4); // 失败
            task.setErrorMessage(e.getMessage());
            taskFileStorage.save(task);
            recordLog(taskId, "ERROR", "分发任务执行失败: " + e.getMessage(), null, null, 500, e);
        }
    }

    @Override
    public List<PublishTask> getAllTasks() {
        List<PublishTask> tasks;
        if (UserContext.isAdmin()) {
            tasks = taskFileStorage.findAll();
        } else {
            tasks = taskFileStorage.findByUserId(UserContext.getUserId());
        }
        
        // 倒序排列（最新的在前）
        tasks.sort(Comparator.comparing(PublishTask::getId).reversed());
        
        // 注入文章标题
        tasks.forEach(task -> {
            articleFileStorage.findById(task.getArticleId())
                .ifPresent(article -> task.setArticleTitle(article.getTitle()));
        });
        
        return tasks;
    }

    @Override
    public List<PublishTask> getTasksByBatchId(Long batchId) {
        List<PublishTask> tasks = taskFileStorage.findByBatchId(batchId);
        if (UserContext.isAdmin()) return tasks;
        Long userId = UserContext.getUserId();
        return tasks.stream()
                .filter(t -> userId.equals(t.getUserId()))
                .collect(Collectors.toList());
    }

    @Override
    public PublishStats getStatistics() {
        PublishStats stats = new PublishStats();
        long accountCount;
        long articleCount;
        List<PublishTask> allTasks;
        
        if (UserContext.isAdmin()) {
            accountCount = accountService.getAllAccounts().size();
            articleCount = articleFileStorage.findAll().size();
            allTasks = taskFileStorage.findAll();
        } else {
            Long userId = UserContext.getUserId();
            accountCount = accountService.getAllAccounts().size();
            articleCount = articleFileStorage.findByUserId(userId).size();
            allTasks = taskFileStorage.findByUserId(userId);
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
                    return accountService.getById(task.getAccountId())
                            .map(Account::getPlatformId)
                            .orElse(0L);
                }, Collectors.counting()));

        List<PublishStats.ChartData> platformData = new ArrayList<>();
        platformCounts.forEach((pid, count) -> {
            com.news.publish.model.entity.Platform p = platformService.getPlatformById(pid);
            String platformName = p != null ? p.getPlatformName() : "未知平台";
            platformData.add(new PublishStats.ChartData(platformName, count));
        });
        stats.setPlatformData(platformData);
        return stats;
    }

    @Override
    public PublishTask getTaskById(Long taskId) {
        return taskFileStorage.findById(taskId).orElse(null);
    }

    @Override
    public List<com.news.publish.model.entity.PublishLog> getLogsByTaskId(Long taskId) {
        PublishTask task = taskFileStorage.findById(taskId).orElseThrow(() -> new RuntimeException("任务不存在"));
        if (!UserContext.isAdmin() && !task.getUserId().equals(UserContext.getUserId())) {
            throw new RuntimeException("无权查看此任务日志");
        }
        try {
            return logRepository.findByTaskIdOrderByCreateTimeDesc(taskId);
        } catch (Exception e) {
            log.warn("查询发布日志失败（可能未使用数据库）: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void deleteTasks(List<Long> taskIds) {
        if (UserContext.isAdmin()) {
             taskIds.forEach(taskFileStorage::deleteById);
        } else {
             Long userId = UserContext.getUserId();
             taskIds.forEach(id -> {
                 taskFileStorage.findById(id).ifPresent(task -> {
                     if (userId.equals(task.getUserId())) {
                         taskFileStorage.deleteById(id);
                     }
                 });
             });
        }
    }
}
