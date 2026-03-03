package com.news.publish.service.impl;

import com.news.publish.model.dto.HotNewsDto;
import com.news.publish.service.HotNewsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// @Service  // 已由 AiHotNewsServiceImpl (@Primary) 替代
public class MockHotNewsServiceImpl implements HotNewsService {

    @Override
    public List<HotNewsDto> fetchHotNewsByKeyword(String keyword, String tab, String platform, String contentType, String domains, String publishTime, String sort) {
        List<HotNewsDto> newsList = new ArrayList<>();
        
        // 模拟抓取过程与假数据
        String[] templates = {
            "%s 行业再次迎来重大突破！",
            "深度解析：为什么 %s 是未来的发展趋势？",
            "专家指出：%s 领域即将洗牌，中小企业如何破局？",
            "重磅！关于 %s 的最新政策解读来了",
            "盘点2024年 %s 最值得关注的十大技术创新"
        };
        
        for (int i = 0; i < 5; i++) {
            HotNewsDto news = new HotNewsDto();
            news.setId(UUID.randomUUID().toString());
            news.setTitle(String.format(templates[i], keyword));
            news.setSummary("这是一篇关于【" + keyword + "】的最新热点快讯，系统通过广域网爬虫引擎在各大主流媒体和垂直社区实时抓取聚合，展示了该关键词下最受网民关注的核心议题。");
            news.setSource(i % 2 == 0 ? "新浪科技" : "全网热搜");
            news.setSourceUrl("https://news.example.com/" + news.getId());
            news.setUrl(news.getSourceUrl());
            news.setHotScore(10000 - i * 1500);
            news.setPublishTime(LocalDateTime.now().minusHours(i * 2));
            newsList.add(news);
        }
        return newsList;
    }

    @Override
    public String fetchArticleContent(String url, String platform, String type) {
        return fetchArticleContent(url);
    }

    @Override
    public String fetchArticleContent(String url) {
        return "这是 MockHotNewsServiceImpl 返回的模拟正文内容。当前系统处于开发模式，通过随机生成的文本模拟真实网页正文提取效果。正文内容通常包含行业分析、技术评论以及市场预测等深度信息。";
    }
}
