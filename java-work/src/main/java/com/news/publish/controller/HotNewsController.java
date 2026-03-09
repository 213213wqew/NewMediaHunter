package com.news.publish.controller;

import com.news.publish.model.dto.HotNewsDto;
import com.news.publish.service.HotNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

@RestController
@RequestMapping("/api/hot-news")
@RequiredArgsConstructor
public class HotNewsController {

    private final HotNewsService hotNewsService;

    private List<String> extractImagesFromHtml(String html) {
        List<String> images = new ArrayList<>();
        if (html == null || html.isEmpty()) return images;
        Document doc = Jsoup.parse(html);
        Elements imgs = doc.select("img");
        imgs.forEach(img -> {
            String src = img.attr("src");
            if (src != null && !src.isEmpty() && !src.startsWith("data:image/svg+xml")) {
                images.add(src);
            }
        });
        return images;
    }

    @GetMapping("/fetch")
    public List<HotNewsDto> fetchNews(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String domains,
            @RequestParam(required = false) String publishTime,
            @RequestParam(required = false) String sort) {
        return hotNewsService.fetchHotNewsByKeyword(keyword, tab, platform, contentType, domains, publishTime, sort);
    }

    @GetMapping("/download")
    public Map<String, String> download(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String domains,
            @RequestParam(required = false) String publishTime,
            @RequestParam(required = false) String sort) {
        String url = "https://www.czgts.cn/v1/hots/" + (tab == null || tab.isEmpty() ? "popular" : tab);
        Map<String, Object> filters = new HashMap<>();
        if (platform != null) filters.put("platform", platform);
        if (contentType != null) filters.put("contentType", contentType);
        if (domains != null) filters.put("domains", domains);
        if (publishTime != null) filters.put("publishTime", publishTime);
        if (sort != null) filters.put("sort", sort);
        
        String path = ((com.news.publish.service.impl.RealWorldHotNewsServiceImpl)hotNewsService).downloadExcel(url, filters);
        return Map.of("path", path == null ? "failed" : path);
    }

    @GetMapping("/article-content")
    public Map<String, Object> fetchArticleContent(
            @RequestParam String url,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String contentType) {
        String content = hotNewsService.fetchArticleContent(url, platform, contentType);
        List<String> images = extractImagesFromHtml(content);
        return Map.of("content", content, "images", images);
    }
}
