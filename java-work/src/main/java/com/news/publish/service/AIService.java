package com.news.publish.service;

import java.util.List;

/**
 * AI 赋能服务接口
 */
public interface AIService {
    
    /**
     * 生成文章摘要
     */
    String generateSummary(String content);

    /**
     * 智能标题优化建议
     */
    List<String> suggestTitles(String title, String content);

    /**
     * 自动提取 SEO 标签
     */
    List<String> extractTags(String content);

    /**
     * 自动提取内容分类
     */
    String extractCategory(String content, String categories);

    /**
     * 全自动撰写文章
     */
    String generateFullArticle(String topic, String outline, Long specId);

    /**
     * 自动匹配随机首图 (如通过 Unsplash)
     */
    String autoMatchImage(String keyword);

    /**
     * AI 内容润色
     */
    String polishContent(String content, Long specId);

    /**
     * 根据内容深度语义匹配多张配图
     */

    /**
     * 万能网页抓取与结构化提取 (RAG 机制)
     * @param url 要抓取的任意网页URL (需带状态则走 InteractiveBrowserService)
     * @param extractionPrompt 让大模型如何提取这堆抓下来的纯文本
     * @return 大模型提纯后的结果 (通常要求它返回 JSON 字符串)
     */
    String scrapeAndAnalyze(String url, String extractionPrompt);
    /**
     * 根据文章内容从给定的热点话题列表中匹配最相关的项
     */
    List<String> matchHotTopics(String content, String hotTopicsJson);

    /**
     * 根据描述生成全新品质大图 (DALL-E 3 / Flux 等)
     */
    String generateImage(String prompt);

    /**
     * 新闻深度分析 (针对 News Aggregator Skill)
     */
    String analyzeNews(String title, String content, Long specId);
}
