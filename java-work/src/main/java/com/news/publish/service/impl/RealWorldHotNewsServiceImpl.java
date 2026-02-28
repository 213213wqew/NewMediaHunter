package com.news.publish.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.publish.model.dto.HotNewsDto;
import com.news.publish.service.HotNewsService;
import com.news.publish.utils.PythonExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 真实世界热点服务实现
 * 使用 Python Skill 实时抓取全网热榜数据
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class RealWorldHotNewsServiceImpl implements HotNewsService {

    private final PythonExecutor pythonExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 注入 AI 模拟实现作为降级方案
    private final AiHotNewsServiceImpl aiFallbackService;

    @Override
    public List<HotNewsDto> fetchHotNewsByKeyword(String keyword) {
        // 1. 定位脚本位置
        // user.dir 在 SpringBoot 运行时即为项目根目录（新闻发布程序），.agents 在其下
        String userDir = System.getProperty("user.dir");
        String scriptPath = userDir + File.separator + ".agents" + File.separator + "skills" + 
                           File.separator + "news-aggregator-skill" + File.separator + "scripts" + 
                           File.separator + "fetch_news.py";
        log.info("Script path: {}, exists: {}", scriptPath, new File(scriptPath).exists());

        // 2. 准备执行参数（排除 GitHub Trending，只保留热点资讯源）
        List<String> args = new ArrayList<>();
        args.add("--source");
        args.add("hackernews,weibo,36kr,v2ex,tencent,wallstreetcn,sina,netease,sohu,cctv,toutiao,baidu");
        args.add("--limit");
        args.add("12");
        if (keyword != null && !keyword.trim().isEmpty()) {
            args.add("--keyword");
            args.add(keyword);
        }

        log.info("Fetching real-time hot news using Skill (Sources: all)...");

        // 3. 执行抓取
        String jsonOutput = pythonExecutor.execute(scriptPath, args);

        if (jsonOutput == null || jsonOutput.trim().isEmpty() || !jsonOutput.contains("[")) {
            log.warn("Skill execution failed or returned invalid JSON. Falling back to AI generation.");
            return aiFallbackService.fetchHotNewsByKeyword(keyword);
        }

        List<HotNewsDto> result = new ArrayList<>();
        // 4. 防御性截取：只取第一个完整 JSON 数组（从第一个 '[' 到最后一个 ']'）
        try {
            int start = jsonOutput.indexOf('[');
            int end = jsonOutput.lastIndexOf(']');
            if (start < 0 || end < 0 || end <= start) {
                log.warn("No valid JSON array found in Python output.");
                return aiFallbackService.fetchHotNewsByKeyword(keyword);
            }
            String cleanJson = jsonOutput.substring(start, end + 1);
            log.info("Python output len={}, preview={}", cleanJson.length(), cleanJson.substring(0, Math.min(200, cleanJson.length())));
            List<Map<String, Object>> rawList = objectMapper.readValue(cleanJson, new TypeReference<>() {});
            log.info("Parsed {} news items from Python", rawList.size());

            for (Map<String, Object> raw : rawList) {
                HotNewsDto dto = new HotNewsDto();
                dto.setId(UUID.randomUUID().toString());
                dto.setTitle(getStr(raw, "title"));
                
                // 真实抓取的数据可能没有 long summary，我们把 title 后面拼接 heat 信息
                String heat = getStr(raw, "heat");
                dto.setSummary(getStr(raw, "content")); // Skill 的 --deep 模式可以抓取 content，但我们暂未开启以保证速度
                if (dto.getSummary() == null || dto.getSummary().isEmpty()) {
                    dto.setSummary("来自 " + getStr(raw, "source") + " 的实时热点，热度指数: " + (heat.isEmpty() ? "推荐" : heat));
                }
                
                dto.setSource(getStr(raw, "source"));
                dto.setUrl(getStr(raw, "url")); // mapped explicitly
                dto.setSourceUrl(getStr(raw, "url")); // optional fallback for frontend
                
                // 将热度转换为数值分数 (简单处理)
                dto.setHotScore(parseHeatToScore(heat));
                
                // 设置发布时间 (处理 Python Skill 返回的字符时间，或者生成 20 小时内的拟真数据)
                dto.setPublishTime(parsePublishTime(getStr(raw, "time")));
                
                // 提取标签 (如果 Skill 提供)
                if (raw.containsKey("tags")) {
                    dto.setTags((List<String>) raw.get("tags"));
                }
                if (dto.getTags() == null) {
                    List<String> tags = new ArrayList<>();
                    tags.add(dto.getSource());
                    
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        tags.add(keyword);
                    }
                    
                    dto.setTags(tags);
                }

                result.add(dto);
            }
        } catch (Exception e) {
            log.error("Failed to parse Skill JSON: {}, Output: {}", e.getMessage(), jsonOutput);
        }

        return result;
    }

    private String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
    }

    @Override
    public String fetchArticleContent(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        
        // 1. 定位脚本位置
        String userDir = System.getProperty("user.dir");
        String scriptPath = userDir + File.separator + ".agents" + File.separator + "skills" + 
                           File.separator + "news-aggregator-skill" + File.separator + "scripts" + 
                           File.separator + "fetch_news.py";

        // 2. 准备执行参数
        List<String> args = new ArrayList<>();
        args.add("--url");
        args.add(url);

        try {
            // 3. 执行抓取
            String jsonOutput = pythonExecutor.execute(scriptPath, args);
            
            if (jsonOutput == null || jsonOutput.trim().isEmpty()) {
                return "";
            }
            
            // 4. 解析结果 (防御性截取: 提取 JSON 对象)
            int start = jsonOutput.indexOf('{');
            int end = jsonOutput.lastIndexOf('}');
            if (start >= 0 && end > start) {
                jsonOutput = jsonOutput.substring(start, end + 1);
            } else {
                return "";
            }
            
            JsonNode root = objectMapper.readTree(jsonOutput);
            return root.path("content").asText("");
        } catch (Exception e) {
            log.error("Failed to fetch article content for URL: {}, Error: {}", url, e.getMessage());
            return "";
        }
    }

    private Integer parseHeatToScore(String heat) {
        if (heat == null || heat.trim().isEmpty()) {
            // 没有热度，根据时间随机生成一个合理的分数 4000-8000
            return 4000 + (int) (Math.random() * 4000);
        }
        try {
            // 尝试提取其中的数字（如 "1119654" 微博热搜，或 "450万" 哔哩哔哩 等）
            // 微博这种上百万的数值我们要适当压缩一下，否则分值差异太大
            String numOnly = heat.replaceAll("[^0-9]", "");
            if (numOnly.isEmpty()) {
                 return 5000 + (int) (Math.random() * 2000);
            }
            long val = Long.parseLong(numOnly);
            
            // 简单规则：万级别的，除以10；更高的由于只是展示热度，压在一个合理范围
            if (val > 1000000) return (int) (val / 1000);
            if (val > 10000) return (int) (val / 10);
            return (int) val;
        } catch (Exception e) {
            return 3000 + (int) (Math.random() * 5000);
        }
    }

    private LocalDateTime parsePublishTime(String timeStr) {
        // 如果没有时间、无法解析或只是相对文本，默认打散在 20 小时内
        LocalDateTime fallbackTime = LocalDateTime.now().minusMinutes((long) (Math.random() * 1200));
        
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return fallbackTime;
        }
        
        try {
            // 尝试解析 yyyy-MM-dd HH:mm:ss
            if (timeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(timeStr, formatter);
            }
        } catch (Exception e) {
            // 忽略异常，使用兜底时间
        }
        
        return fallbackTime;
    }
}
