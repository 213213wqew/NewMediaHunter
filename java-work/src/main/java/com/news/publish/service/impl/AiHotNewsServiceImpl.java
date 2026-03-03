package com.news.publish.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.publish.model.dto.HotNewsDto;
import com.news.publish.service.AIService;
import com.news.publish.service.HotNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于大模型的热点新闻生成服务
 * 调用用户配置的 AI 模型，让其根据关键词"推断"当前可能的热点话题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiHotNewsServiceImpl implements HotNewsService {

    private final AIService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROMPT_TEMPLATE = """
            你是一个资深的内容运营专家，熟悉中国各大主流媒体的报道风格。
            请根据关键词【%s】，模拟生成当前互联网上最热门的 8 条真实感资讯，以严格的 JSON 数组格式返回，不要有任何其他内容。
            
            每条资讯的格式如下：
            {
              "title": "吸引眼球的标题（20-35字）",
              "summary": "深度摘要，介绍核心观点（80-120字）",
              "source": "真实媒体名称（如：新华网、36氪、人民日报、澎湃新闻、钛媒体、知乎日报等）",
              "sourceUrl": "对应媒体的主站域名示例链接（如：https://www.36kr.com/p/example）",
              "hotScore": 热度指数（1000-15000之间的整数）,
              "tags": ["标签1", "标签2", "标签3"]
            }
            
            要求：
            1. 标题多样：综合新闻、深度分析、政策解读、行业洞察都要有
            2. 来源多样：不同媒体风格各异，体现媒体差异化
            3. 内容贴近当前热点，符合中国互联网语境
            4. 只返回 JSON 数组，不要有任何说明文字
            """;

    @Override
    public List<HotNewsDto> fetchHotNewsByKeyword(String keyword, String tab, String platform, String contentType, String domains, String publishTime, String sort) {
        try {
            String prompt = String.format(PROMPT_TEMPLATE, keyword);
            String aiResponse = aiService.generateSummary(prompt);

            // 提取 JSON 数组（AI 有时会在前后加说明文字）
            String json = extractJson(aiResponse);
            List<Map<String, Object>> rawList = objectMapper.readValue(json, new TypeReference<>() {});

            List<HotNewsDto> result = new ArrayList<>();
            for (Map<String, Object> raw : rawList) {
                HotNewsDto dto = new HotNewsDto();
                dto.setId(UUID.randomUUID().toString());
                dto.setTitle(str(raw, "title"));
                dto.setSummary(str(raw, "summary"));
                dto.setSource(str(raw, "source"));
                dto.setSourceUrl(str(raw, "sourceUrl"));
                dto.setUrl(str(raw, "sourceUrl"));
                dto.setHotScore(num(raw, "hotScore"));
                dto.setPublishTime(LocalDateTime.now().minusMinutes((long)(Math.random() * 120)));
                Object tagsObj = raw.get("tags");
                if (tagsObj instanceof List<?> tagsList) {
                    dto.setTags(tagsList.stream().map(Object::toString).toList());
                }
                result.add(dto);
            }
            return result;
        } catch (RuntimeException e) {
            // 如果是大模型接口直接报错（如：欠费、鉴权失败），直接抛出给前端
            if (e.getMessage() != null && (e.getMessage().contains("调用失败") || e.getMessage().contains("错误") || e.getMessage().contains("error") || e.getMessage().contains("insufficient"))) {
                throw e;
            }
            log.warn("AI 热点提取或解析失败，降级到 Mock: {}", e.getMessage());
            return fallback(keyword);
        } catch (Exception e) {
            log.warn("AI 热点生成未知异常，降级到 Mock: {}", e.getMessage());
            return fallback(keyword);
        }
    }

    @Override
    public String fetchArticleContent(String url, String platform, String type) {
        return fetchArticleContent(url);
    }

    @Override
    public String fetchArticleContent(String url) {
        // AI 模拟生成的热点通常没有真实详情页链接，此处直接返回一个模拟的正文
        return "这是基于 AI 模拟热点生成的文章正文内容。在真实环境下，系统将通过正文提取算法从目标 URL 抓取完整内容。";
    }

    /** 从 AI 响应中提取 JSON 数组部分 */
    private String extractJson(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new RuntimeException("AI 响应中未找到 JSON 数组");
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
    }

    private int num(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 5000; }
    }

    /** 降级 Mock 数据（AI 调用失败时） */
    private List<HotNewsDto> fallback(String keyword) {
        List<HotNewsDto> list = new ArrayList<>();
        String[][] data = {
            {keyword + " 行业迎来重大突破，专家解读背后逻辑", "新华网", "https://www.xinhuanet.com/tech/example"},
            {"深度：为什么 " + keyword + " 将成为2025年最热赛道", "36氪", "https://www.36kr.com/p/example"},
            {keyword + " 领域龙头企业最新动态深度分析", "钛媒体", "https://www.tmtpost.com/example"},
            {"关于 " + keyword + " 的最新政策解读全梳理", "人民网", "https://www.people.com.cn/example"},
        };
        for (String[] d : data) {
            HotNewsDto dto = new HotNewsDto();
            dto.setId(UUID.randomUUID().toString());
            dto.setTitle(d[0]);
            dto.setSummary("这是一篇关于【" + keyword + "】的深度资讯，涵盖行业最新动态、政策变化及市场趋势分析。");
            dto.setSource(d[1]);
            dto.setSourceUrl(d[2]);
            dto.setUrl(d[2]);
            dto.setHotScore((int)(Math.random() * 10000) + 2000);
            dto.setPublishTime(LocalDateTime.now().minusMinutes((long)(Math.random() * 60)));
            dto.setTags(List.of(keyword, "热点", "深度"));
            list.add(dto);
        }
        return list;
    }
}
