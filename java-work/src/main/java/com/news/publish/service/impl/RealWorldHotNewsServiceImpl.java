package com.news.publish.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.publish.model.dto.HotNewsDto;
import com.news.publish.service.HotNewsService;
import com.news.publish.service.automation.ArticleExtractorService;
import com.news.publish.service.automation.InteractiveBrowserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;

/**
 * 真实世界热点服务实现
 * 使用模拟浏览器打开创作罐头管理后台热点页，从表格中提取热点信息（不再使用 Skills）
 * 与创作罐头对应：全网热榜=network，全网爆文=article，低粉爆款=popular
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class RealWorldHotNewsServiceImpl implements HotNewsService {

    private static final String BASE_URL = "https://www.czgts.cn/v1/hots/";

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Lazy
    private final InteractiveBrowserService interactiveBrowserService;
    private final ArticleExtractorService articleExtractorService;
    private final AiHotNewsServiceImpl aiFallbackService;

    private String resolveUrl(String tab) {
        if ("network".equals(tab)) return BASE_URL + "network";
        if ("article".equals(tab)) return BASE_URL + "article";
        return BASE_URL + "popular"; // 低粉爆款 或默认
    }

    @Override
    public List<HotNewsDto> fetchHotNewsByKeyword(String keyword, String tab, String platform, String contentType, String domains, String publishTime, String sort) {
        String url = resolveUrl(tab);
        log.info("Fetching hot news via browser from: {} filters: platform={}, contentType={}, domains={}, publishTime={}, sort={}", url, platform, contentType, domains, publishTime, sort);
        Map<String, Object> filters = new HashMap<>();
        if (platform != null && !platform.isEmpty()) filters.put("platform", platform);
        if (contentType != null && !contentType.isEmpty()) filters.put("contentType", contentType);
        if (domains != null && !domains.isEmpty()) filters.put("domains", domains);
        if (publishTime != null && !publishTime.isEmpty()) filters.put("publishTime", publishTime);
        if (sort != null && !sort.isEmpty()) filters.put("sort", sort);
        if (keyword != null && !keyword.trim().isEmpty()) filters.put("keyword", keyword.trim());
        String jsonOutput;
        try {
            jsonOutput = interactiveBrowserService.getHotspotTableFromPage(url, filters);
        } catch (Exception e) {
            log.warn("Browser fetch failed: {}. Falling back to AI generation.", e.getMessage());
            return aiFallbackService.fetchHotNewsByKeyword(keyword, tab, platform, contentType, domains, publishTime, sort);
        }
        if (jsonOutput == null || jsonOutput.trim().isEmpty() || !jsonOutput.contains("[")) {
            log.warn("Browser returned no table data. Falling back to AI generation.");
            return aiFallbackService.fetchHotNewsByKeyword(keyword, tab, platform, contentType, domains, publishTime, sort);
        }
        List<HotNewsDto> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(jsonOutput, new TypeReference<>() {});
            log.info("Parsed {} hotspot rows from page", rawList.size());
            for (Map<String, Object> raw : rawList) {
                HotNewsDto dto = mapTableRowToDto(raw, keyword, url);
                if (dto.getTitle() != null && !dto.getTitle().isEmpty()) {
                    result.add(dto);
                }
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                List<HotNewsDto> filtered = result.stream()
                        .filter(d -> (d.getTitle() != null && d.getTitle().contains(keyword))
                                || (d.getTags() != null && d.getTags().stream().anyMatch(t -> t != null && t.contains(keyword))))
                        .toList();
                if (!filtered.isEmpty()) result = filtered;
            }
        } catch (Exception e) {
            log.error("Failed to parse hotspot table JSON: {}", e.getMessage());
            return aiFallbackService.fetchHotNewsByKeyword(keyword, tab, platform, contentType, domains, publishTime, sort);
        }
        if (result.isEmpty()) {
            log.warn("No hotspot items after parse. Falling back to AI generation.");
            return aiFallbackService.fetchHotNewsByKeyword(keyword, tab, platform, contentType, domains, publishTime, sort);
        }
        return result;
    }

    private HotNewsDto mapTableRowToDto(Map<String, Object> raw, String keyword, String pageUrl) {
        // 表格同一行：时间能拿到说明结构一致，阅读/评论/点赞可能在嵌套 item/record/content 里，合并到一层再取
        Map<String, Object> row = flattenRow(raw);
        HotNewsDto dto = new HotNewsDto();
        dto.setId(UUID.randomUUID().toString());
        String title = getStrOr(row, "title", "name", "articleTitle");
        dto.setTitle(title);
        String platform = getStrOr(row, "platform", "platformName", "source");
        String category = getStrOr(row, "category", "domain", "field", "type");
        // 创作罐头 API 字段：readCnt=播放量, commentCnt=评论量, diggCnt=点赞量, url=文章地址
        String reads = formatReadsFromRaw(row);
        String comments = getEngagementFromRaw(row, "commentCnt", "commentCount", "comments", "comment_count", "commentNum", "评论量", "replyCount", "reply_count");
        if (comments.isEmpty()) comments = formatEngagementFromStatsArray(row, 1);
        String likes = getEngagementFromRaw(row, "diggCnt", "likeCount", "likes", "diggCount", "like_count", "likeNum", "digg_count", "点赞量", "favorCount", "upCount");
        if (likes.isEmpty()) likes = formatEngagementFromStatsArray(row, 2);
        String publishTimeStr = getStrOr(row, "publishTime", "publishTimeStr", "createTime", "time");
        Object publishTimeObj = getAny(row, "publishTime", "createTime", "time", "publishTimeStamp");
        dto.setSource(platform.isEmpty() ? "创作罐头" : platform);
        String topic = category.isEmpty() ? "热点" : category;
        dto.setSummary(String.format("这是一篇关于【%s】的深度资讯，来自 %s。阅读 %s，评论 %s，点赞 %s。",
                topic, dto.getSource(), reads, comments, likes));
        String articleUrl = normalizeArticleUrl(getStrOr(row, "url", "link", "articleUrl", "articleLink", "article_link", "contentUrl", "detailUrl", "sourceUrl"));
        dto.setUrl(articleUrl.isEmpty() ? "" : articleUrl);
        dto.setSourceUrl(articleUrl.isEmpty() ? pageUrl : articleUrl);
        dto.setHotScore(parseReadsToScore(reads));
        dto.setPublishTime(parseTablePublishTime(publishTimeStr, publishTimeObj));
        List<String> tags = new ArrayList<>();
        if (!category.isEmpty()) tags.add(category);
        if (!platform.isEmpty()) tags.add(platform);
        tags.add("热点");
        tags.add("深度");
        if (keyword != null && !keyword.trim().isEmpty()) tags.add(keyword);
        dto.setTags(tags);
        return dto;
    }

    /** 把 item/record/content/data 等嵌套合并到一层，和表格列一致：时间能取到，阅读/评论/点赞也在同一行里 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenRow(Map<String, Object> raw) {
        Map<String, Object> out = new HashMap<>(raw);
        for (String key : new String[]{"item", "record", "content", "data", "info"}) {
            Object nest = raw.get(key);
            if (nest instanceof Map) {
                ((Map<String, Object>) nest).forEach(out::putIfAbsent);
            }
        }
        return out;
    }

    private String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString().trim();
    }

    /** 按多个候选 key 取值，第一个非空即返回，用于兼容不同 API 字段名 */
    private String getStrOr(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String s = getStr(map, key);
            if (s != null && !s.isEmpty()) return s;
        }
        return "";
    }

    /** 从 raw 或嵌套的 data/stats 中取互动数（评论、点赞），支持多种字段名及 stats 数组 [读,评,赞] */
    private String getEngagementFromRaw(Map<String, Object> raw, String... keys) {
        Object v = getFromRawOrNested(raw, keys);
        if (v == null) return "";
        if (v instanceof Number) return formatInteger(((Number) v).longValue());
        String s = v.toString().trim();
        return s.isEmpty() ? "" : s;
    }

    /** 千分位不强制，仅去逗号后返回；大数原样返回 */
    private String formatInteger(long n) {
        return String.valueOf(n);
    }

    /** 从 raw 或 raw.data / raw.stats 的 Map 中按 keys 顺序取第一个非空值 */
    @SuppressWarnings("unchecked")
    private Object getFromRawOrNested(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object v = raw.get(key);
            if (v != null) return v;
        }
        for (String nest : new String[]{"data", "stats", "stat", "counts"}) {
            Object nestObj = raw.get(nest);
            if (!(nestObj instanceof Map)) continue;
            Map<String, Object> nestMap = (Map<String, Object>) nestObj;
            for (String key : keys) {
                Object v = nestMap.get(key);
                if (v != null) return v;
            }
        }
        return null;
    }

    /** 从 raw.stats / raw.counts 数组取第 index 项（0=阅读 1=评论 2=点赞），部分 API 用 [读,评,赞] 返回 */
    @SuppressWarnings("unchecked")
    private Object getFromStatsArray(Map<String, Object> raw, int index) {
        for (String key : new String[]{"stats", "counts", "stat", "numbers"}) {
            Object arr = raw.get(key);
            if (arr instanceof List && ((List<?>) arr).size() > index) {
                Object v = ((List<?>) arr).get(index);
                if (v != null) return v;
            }
        }
        for (String nest : new String[]{"data", "item", "record"}) {
            Object nestObj = raw.get(nest);
            if (!(nestObj instanceof Map)) continue;
            Map<String, Object> nestMap = (Map<String, Object>) nestObj;
            for (String key : new String[]{"stats", "counts"}) {
                Object arr = nestMap.get(key);
                if (arr instanceof List && ((List<?>) arr).size() > index) {
                    Object v = ((List<?>) arr).get(index);
                    if (v != null) return v;
                }
            }
        }
        return null;
    }

    /** 把 stats 数组第 index 项格式化为字符串（评论/点赞用） */
    private String formatEngagementFromStatsArray(Map<String, Object> raw, int index) {
        Object v = getFromStatsArray(raw, index);
        if (v == null) return "";
        if (v instanceof Number) return String.valueOf(((Number) v).longValue());
        return v.toString().trim();
    }

    private Object getAny(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null) return v;
        }
        return null;
    }

    /** 规范化文章链接：保留 https 的完整 URL，用于「查看」「复制链接」 */
    private String normalizeArticleUrl(String url) {
        if (url == null || (url = url.trim()).isEmpty()) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (!url.startsWith("http")) return "";
        return url;
    }

    /** 从 raw 中取阅读/播放量并格式化为展示（创作罐头 API 为 readCnt，如 "421477" -> "42.1万"） */
    private String formatReadsFromRaw(Map<String, Object> raw) {
        String[] keys = {"readCnt", "readCount", "viewCount", "playCount", "reads", "read_count", "view_count", "play_count", "阅读量", "播放量", "readNum", "viewNum", "exposeCount", "showCount"};
        Object v = getFromRawOrNested(raw, keys);
        if (v == null) v = getFromStatsArray(raw, 0);
        if (v == null) return "";
        long n;
        if (v instanceof Number) {
            n = ((Number) v).longValue();
        } else {
            String s = v.toString().trim();
            if (s.isEmpty()) return "";
            try {
                n = Long.parseLong(s.replace(",", ""));
            } catch (NumberFormatException e) {
                return s;
            }
        }
        if (n >= 10000) return String.format("%.1f万", n / 10000.0);
        return String.valueOf(n);
    }

    public String downloadExcel(String url, Map<String, Object> filters) {
        return interactiveBrowserService.downloadHotspotFile(url, filters);
    }

    @Override
    public String fetchArticleContent(String url, String platform, String type) {
        if (url == null || url.trim().isEmpty()) return "";
        try {
            return articleExtractorService.extractContent(url, platform, type);
        } catch (Exception e) {
            log.error("Failed to fetch article content for URL: {}, Error: {}", url, e.getMessage());
            return "";
        }
    }

    // 兼容旧接口（如果 interface 还没改）
    public String fetchArticleContent(String url) {
        return fetchArticleContent(url, null, null);
    }

    /** 将表格中的「阅读(播放)」如 "95.5万" 转为热度分数 */
    private Integer parseReadsToScore(String reads) {
        if (reads == null || reads.trim().isEmpty()) return 5000;
        try {
            String s = reads.trim().replace(",", "");
            boolean wan = s.contains("万");
            String numOnly = s.replaceAll("[^0-9.]", "");
            if (numOnly.isEmpty()) return 5000;
            double val = Double.parseDouble(numOnly);
            if (wan) val *= 10000;
            if (val > 1000000) return (int) (val / 1000);
            if (val > 10000) return (int) (val / 10);
            return (int) val;
        } catch (Exception e) {
            return 5000;
        }
    }

    /** 解析表格中的发布时间：支持 "03-01 15:18"、"yyyy-MM-dd HH:mm:ss"、时间戳（毫秒/秒） */
    private LocalDateTime parseTablePublishTime(String timeStr, Object publishTimeObj) {
        // 1) 时间戳（毫秒或秒）
        if (publishTimeObj instanceof Number) {
            long ms = ((Number) publishTimeObj).longValue();
            if (ms < 1_000_000_000_000L) ms *= 1000;
            try {
                return LocalDateTime.ofEpochSecond(ms / 1000, (int) (ms % 1000 * 1_000_000), java.time.ZoneOffset.ofHours(8));
            } catch (Exception ignored) {}
        }
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return LocalDateTime.now().minusMinutes((long) (Math.random() * 120));
        }
        try {
            String s = timeStr.trim();
            if (s.matches("\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
                int year = Year.now().getValue();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                return LocalDateTime.parse(year + "-" + s, formatter);
            }
            if (s.matches("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?")) {
                s = s.replace('T', ' ').trim();
                DateTimeFormatter formatter = s.length() > 16
                        ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                return LocalDateTime.parse(s.length() > 16 ? s.substring(0, 19) : s, formatter);
            }
        } catch (Exception ignored) {}
        return LocalDateTime.now().minusMinutes((long) (Math.random() * 120));
    }
}
