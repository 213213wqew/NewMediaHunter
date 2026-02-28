package com.news.publish.controller;

import com.news.publish.service.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;

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
    public Map<String, String> generateArticle(@RequestBody Map<String, String> request) {
        String topic = request.get("topic");
        String outline = request.getOrDefault("outline", "综合分析");
        String html = aiService.generateFullArticle(topic, outline);
        return Map.of("content", html);
    }

    @PostMapping("/match-image")
    public Map<String, String> matchImage(@RequestBody Map<String, String> request) {
        String keyword = request.getOrDefault("keyword", "technology");
        String url = aiService.autoMatchImage(keyword);
        return Map.of("url", url);
    }

    @PostMapping("/polish")
    public Map<String, String> polish(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String result = aiService.polishContent(content);
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
}
