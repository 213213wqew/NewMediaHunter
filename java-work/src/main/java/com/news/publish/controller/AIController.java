package com.news.publish.controller;

import com.news.publish.model.entity.PlatformTask;
import com.news.publish.repository.PlatformTaskRepository;
import com.news.publish.service.AIService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AIController {

    @Autowired
    private AIService aiService;

    @Autowired
    private PlatformTaskRepository platformTaskRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/summary")
    public String getSummary(@RequestBody Map<String, String> request) {
        return aiService.generateSummary(request.get("content"));
    }

    @PostMapping("/suggest-titles")
    public List<String> suggestTitles(@RequestBody Map<String, String> request) {
        return aiService.suggestTitles(request.get("title"), request.get("content"));
    }

    @PostMapping("/extract-tags")
    public List<String> extractTags(@RequestBody Map<String, String> request) {
        return aiService.extractTags(request.get("content"));
    }

    @PostMapping("/extract-category")
    public String extractCategory(@RequestBody Map<String, String> request) {
        return aiService.extractCategory(request.get("content"), request.get("categories"));
    }

    @PostMapping("/generate-article")
    public Map<String, String> generateArticle(@RequestBody Map<String, Object> request) {
        String topic = (String) request.get("topic");
        String outline = (String) request.getOrDefault("outline", "综合分析");
        Long specId = request.get("specId") != null ? Long.valueOf(request.get("specId").toString()) : null;
        String html = aiService.generateFullArticle(topic, outline, specId);
        return Map.of("content", html);
    }

    @PostMapping("/match-image")
    public Map<String, String> matchImage(@RequestBody Map<String, String> request) {
        String keyword = request.getOrDefault("keyword", "technology");
        String url = aiService.autoMatchImage(keyword);
        return Map.of("url", url);
    }

    @PostMapping("/generate-image")
    public Map<String, String> generateImage(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        String url = aiService.generateImage(prompt);
        return Map.of("url", url);
    }

    @PostMapping("/polish")
    public Map<String, String> polish(@RequestBody Map<String, Object> request) {
        String content = (String) request.get("content");
        Long specId = request.get("specId") != null ? Long.valueOf(request.get("specId").toString()) : null;
        String result = aiService.polishContent(content, specId);
        return Map.of("content", result);
    }

    @PostMapping("/suggest-images")
    public List<String> suggestImages(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        return aiService.matchParagraphImages(content);
    }

    @PostMapping("/scrape-analyze")
    public Map<String, String> scrapeAndAnalyze(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        String prompt = request.getOrDefault("prompt", "提取页面核心内容");
        String result = aiService.scrapeAndAnalyze(url, prompt);
        return Map.of("result", result);
    }

    @GetMapping("/baijiahao-tasks")
    public Map<String, String> getBaijiahaoTasks() {
        String url = "https://baijiahao.baidu.com/builder/bjh-activity/taskSquare?aside=0";
        String prompt = "请找出页面上百家号所有的热门任务/话题名称（如 #发现不一样的生活#），以及对应的参与人数或热度（如 6.5w人参与 或 热度100w ）。\n" +
                "你必须严格将其提取为只包含话题数据的 JSON 对象数组。绝对不要包含任何多余文字，不要使用 ```json 这类的标记！\n" +
                "JSON 数组的格式必须严格形如：\n" +
                "[\n" +
                "  {\"topic\": \"#A股大涨#\", \"participants\": \"6.5w人参与\"},\n" +
                "  {\"topic\": \"#春日摄影大赛#\", \"participants\": \"12w人参与\"}\n" +
                "]";
        String result = aiService.scrapeAndAnalyze(url, prompt);
        return Map.of("result", result);
    }
    @PostMapping("/match-hot-topics")
    public List<String> matchHotTopics(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String hotTopicsJson = request.get("hotTopicsJson");
        return aiService.matchHotTopics(content, hotTopicsJson);
    }

    @Transactional
    @PostMapping("/platforms/{platKey}/sync-tasks")
    public Map<String, Object> syncPlatformTasks(@PathVariable String platKey) {
        log.info("接收到平台同步请求: {}", platKey);
        String url = "";
        String normalizedKey = platKey;
        if ("bjh".equals(platKey) || "baijiahao".equals(platKey)) {
            url = "https://baijiahao.baidu.com/builder/bjh-activity/taskSquare?aside=0";
            normalizedKey = "bjh"; // 统一存储为 bjh
        } else {
            return Map.of("success", false, "message", "暂不支持该平台热点同步: " + platKey);
        }

        String prompt = "请找出页面上百家号所有的热门任务/话题名称（如 #发现不一样的生活#），以及对应的参与人数。\n" +
                "你必须严格将其提取为只包含话题数据的 JSON 对象数组。绝对不要包含任何多余文字，不要使用 markdown 代码块。\n" +
                "JSON 数组的格式必须严格形如：\n" +
                "[\n" +
                "  {\"topic\": \"#A股大涨#\", \"participants\": \"6.5w人参与\"},\n" +
                "  {\"topic\": \"#春日摄影大赛#\", \"participants\": \"12w人参与\"}\n" +
                "]";

        try {
            // 假设有个 getContent 或者直接用 scrapeAndAnalyze
            // 为了调试，我们还是调用原来的。但由于 scrapeAndAnalyze 内部已经抓取了，我们直接看 AI 结果。
            // 改进：直接在 Controller 这一层做一些基本判断
            
            String resultJson = aiService.scrapeAndAnalyze(url, prompt);
            log.info("AI 原始返回内容: {}", resultJson);
            
            if (resultJson.contains("尚未配置 AI")) {
                return Map.of("success", false, "message", "请先配置 AI 模型");
            }

            // 检查常用关键词判断是否是登录页
            if (resultJson.contains("[]") || resultJson.length() < 20) {
                log.warn("AI 未能识别到热点，可能页面内容不足。请检查是否需要登录。");
                // 暂时返回 0 条，但在 message 里给个提示
                return Map.of("success", true, "count", 0, "message", "抓取到了内容但未识别到话题。请确保浏览器已登录百家号。");
            }
            
            // 简单清洗一下结果，防止 AI 带了 markdown
            String cleanJson = resultJson.replace("```json", "").replace("```", "").trim();
            List<Map<String, String>> tasks = objectMapper.readValue(cleanJson, new TypeReference<>() {});
            
            // 先清除旧数据
            platformTaskRepository.deleteByPlatformKey(normalizedKey);
            
            // 保存新数据
            if (tasks != null && !tasks.isEmpty()) {
                for (Map<String, String> taskMap : tasks) {
                    PlatformTask task = new PlatformTask();
                    task.setPlatformKey(normalizedKey);
                    task.setTopic(taskMap.get("topic"));
                    task.setExtraInfo(taskMap.get("participants"));
                    platformTaskRepository.save(task);
                }
            }
            
            log.info("平台 {} 热点同步完成，共 {} 条数据", normalizedKey, tasks == null ? 0 : tasks.size());
            return Map.of("success", true, "count", tasks == null ? 0 : tasks.size());
        } catch (Exception e) {
            log.error("同步平台 {} 任务失败: {}", platKey, e.getMessage());
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @GetMapping("/platforms/{platKey}/tasks")
    public List<PlatformTask> getPlatformTasks(@PathVariable String platKey) {
        return platformTaskRepository.findByPlatformKey(platKey);
    }
}
