package com.news.publish.controller;

import com.news.publish.model.dto.HotNewsDto;
import com.news.publish.service.HotNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hot-news")
@RequiredArgsConstructor
public class HotNewsController {

    private final HotNewsService hotNewsService;

    @GetMapping("/fetch")
    public List<HotNewsDto> fetchNews(@RequestParam(required = false, defaultValue = "") String keyword) {
        return hotNewsService.fetchHotNewsByKeyword(keyword);
    }

    @GetMapping("/article-content")
    public Map<String, String> fetchArticleContent(@RequestParam String url) {
        String content = hotNewsService.fetchArticleContent(url);
        return Map.of("content", content);
    }
}
