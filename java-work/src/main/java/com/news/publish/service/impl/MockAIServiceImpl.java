package com.news.publish.service.impl;

import com.news.publish.service.AIService;
import java.util.Arrays;
import java.util.List;

/**
 * AI 服务 Mock 实现 (已保留，用于测试和后备)
 * 注意：正式注入已切换到 RealAIServiceImpl (@Primary)
 */
// @Service  // 已由 RealAIServiceImpl (@Primary) 替代
public class MockAIServiceImpl implements AIService {

    @Override
    public String generateSummary(String content) {
        if (content == null || content.length() < 20) return "内容过短，无法生成摘要。";
        // 模拟 AI 提取摘要逻辑
        return "【AI 摘要】" + content.substring(0, Math.min(content.length(), 100)).replaceAll("<[^>]*>", "") + "...";
    }

    @Override
    public List<String> suggestTitles(String title, String content) {
        return Arrays.asList(
            "🔥 震惊！" + title + "背后的真相",
            "深度解析：" + title + "如何改变行业格局？",
            "实战手册：关于" + title + "，你必须知道的 5 件事"
        );
    }

    @Override
    public List<String> extractTags(String content) {
        return Arrays.asList("科技", "深度调查", "热门趋势", "资讯更新");
    }

    @Override
    public String extractCategory(String content, String categories) {
        return "科技互联网"; // Mock fallback
    }

    @Override
    public String generateFullArticle(String topic, String outline) {
        // 模拟生成完整文章的 HTML 内容
        return "<h2>摘要导读</h2>" +
               "<p>针对【<strong>" + topic + "</strong>】这一话题，近期的动向引发了广泛关注。本文将带您深入探讨背后的核心逻辑与行业趋势。</p>" +
               "<h2>一、核心背景与现状</h2>" +
               "<p>根据最新数据，该领域正在经历快速的技术演进。传统模式正在被颠覆，" + outline + " 更是成为了关键的爆发点。业界专家指出，这是不可逆转的洪流。</p>" +
               "<h2>二、关键挑战与机遇</h2>" +
               "<p>机遇总是伴随着挑战。在快速发展的过程中，合规性、技术瓶颈以及市场接受度都成为了绕不开的话题。但正是这些痛点，孕育了无数创新的可能。</p>" +
               "<h2>三、未来展望</h2>" +
               "<p>展望未来，【" + topic + "】赛道将持续火热，资金和人才的涌入会进一步加速其成熟。让我们拭目以待。</p>" +
               "<p><em>（AI 自动生成，仅供参考）</em></p>";
    }

    @Override
    public String autoMatchImage(String keyword) {
        try {
            String encoded = java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
            return "https://source.unsplash.com/800x400/?" + encoded;
        } catch (Exception e) {
            return "https://source.unsplash.com/800x400/?news";
        }
    }

    @Override
    public String generateImage(String prompt) {
        // Mock 实现：直接返回一个 Unsplash 随机图
        return autoMatchImage(prompt);
    }

    @Override
    public String polishContent(String content) {
        if (content == null || content.length() < 10) return content;
        // 模拟 AI 润色逻辑：清洗冗余，增强修辞
        return "【AI 深度润色版】\n" + 
               content.replace("<p>", "<p>✨ ")
                      .replace("挑战", "机遇与突破")
                      .replace("关注", "强势聚焦")
                      .replace("，“", "，“精准捕捉到的")
               + "\n<p><em>(润色说明：优化了关键词表达，增强了阅读冲击力)</em></p>";
    }

    @Override
    public List<String> matchParagraphImages(String content) {
        // 模拟从正文提取关键词并匹配配图
        return Arrays.asList(
            "https://source.unsplash.com/800x450/?office,business",
            "https://source.unsplash.com/800x450/?technology,city",
            "https://source.unsplash.com/800x450/?teamwork,abstract"
        );
    }

    @Override
    public String scrapeAndAnalyze(String url, String extractionPrompt) {
        // 模拟网页提取与解析返回
        return "[\n" +
            "  {\"topic\": \"#A股大涨#\", \"participants\": \"50w人参与\"},\n" +
            "  {\"topic\": \"#春日摄影大赛#\", \"participants\": \"12w人参与\"}\n" +
            "]";
    }
    @Override
    public List<String> matchHotTopics(String content, String hotTopicsJson) {
        return Arrays.asList("#Mock热点1#", "#Mock热点2#");
    }

    @Override
    public String analyzeNews(String title, String content) {
        if (title == null && content == null) return "{}";
        // Mock 新闻深度分析：返回简单结构化结果
        return "{\"title\":\"" + (title != null ? title : "") + "\",\"summary\":\"【Mock 分析】基于标题与正文的模拟深度解读。\",\"keywords\":[\"热点\",\"趋势\",\"解读\"]}";
    }
}
