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
}
