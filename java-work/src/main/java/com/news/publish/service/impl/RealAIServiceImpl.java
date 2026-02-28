package com.news.publish.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.publish.model.entity.AiConfig;
import com.news.publish.service.AIService;
import com.news.publish.service.AiConfigService;
import com.news.publish.service.automation.InteractiveBrowserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 真实大模型 AI 服务实现
 * 支持：OpenAI / Gemini / Claude / 通义千问 / 智谱 GLM / DeepSeek / Ollama / 自定义
 */
@Slf4j
@Primary
@Service
public class RealAIServiceImpl implements AIService {

    private final AiConfigService aiConfigService;
    private final InteractiveBrowserService interactiveBrowserService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------ 用于测试连接时的临时实例（direct config，不依赖 Spring 上下文）------
    private final AiConfig directConfig;

    /** Spring 容器注入 —— 正常业务流程使用 */
    @org.springframework.beans.factory.annotation.Autowired
    public RealAIServiceImpl(AiConfigService aiConfigService, @org.springframework.context.annotation.Lazy InteractiveBrowserService interactiveBrowserService) {
        this.aiConfigService = aiConfigService;
        this.interactiveBrowserService = interactiveBrowserService;
        this.directConfig = null;
    }

    /** 临时实例 —— 仅用于 /api/ai-config/test 接口 */
    public RealAIServiceImpl(AiConfig directConfig) {
        this.aiConfigService = null;
        this.interactiveBrowserService = null;
        this.directConfig = directConfig;
    }

    // -------- 获取当前有效配置 --------
    private AiConfig resolveConfig() {
        if (directConfig != null) return directConfig;
        if (aiConfigService != null) return aiConfigService.getConfig();
        return null;
    }

    // =========================================================================
    // 核心：调用 AI 接口
    // =========================================================================

    /**
     * 统一聊天调用入口（OpenAI 兼容格式）
     * 适配：openai / qianwen / zhipu / deepseek / ollama / custom
     */
    private String chatCompletions(AiConfig cfg, String systemPrompt, String userContent) {
        String url = cfg.getBaseUrl();
        if (!url.endsWith("/")) url += "/";
        String endpoint = url + "chat/completions";

        Map<String, Object> body = Map.of(
            "model", cfg.getModelName(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
            ),
            "max_tokens", 8192
        );

        try {
            RestClient client = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                .build();

            String response = client.post()
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            if (root.has("error")) {
                String errMsg = root.at("/error/message").asText("未知错误");
                throw new RuntimeException(errMsg);
            }
            return root.at("/choices/0/message/content").asText("AI 没有返回有效内容");
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String respBody = e.getResponseBodyAsString();
            try {
                JsonNode errRoot = objectMapper.readTree(respBody);
                if (errRoot.has("error")) {
                    String errMsg = errRoot.at("/error/message").asText(e.getStatusText());
                    throw new RuntimeException(errMsg);
                }
            } catch (Exception parseEx) {
                 // ignore parse error, use fallback
            }
            throw new RuntimeException("AI API 错误: HTTP " + e.getStatusCode() + " - " + respBody);
        } catch (Exception e) {
            log.error("AI 接口调用失败: provider={}, error={}", cfg.getProvider(), e.getMessage());
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException("AI 接口调用失败：" + e.getMessage());
        }
    }

    /**
     * Google Gemini 专属调用（使用 REST v1beta generateContent）
     */
    private String callGemini(AiConfig cfg, String prompt) {
        String url = cfg.getBaseUrl();
        if (!url.endsWith("/")) url += "/";
        String model = cfg.getModelName().isBlank() ? "gemini-pro" : cfg.getModelName();
        String endpoint = url + "models/" + model + ":generateContent?key=" + cfg.getApiKey();

        Map<String, Object> body = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            ))
        );

        try {
            RestClient client = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

            String response = client.post().body(body).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.has("error")) {
               throw new RuntimeException(root.at("/error/message").asText("Gemini 发生未知错误"));
            }
            return root.at("/candidates/0/content/parts/0/text").asText("Gemini 没有返回有效内容");
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String respBody = e.getResponseBodyAsString();
            try {
                JsonNode errRoot = objectMapper.readTree(respBody);
                if (errRoot.has("error")) {
                    throw new RuntimeException(errRoot.at("/error/message").asText(e.getStatusText()));
                }
            } catch (Exception parseEx) {}
            throw new RuntimeException("Gemini API 错误: HTTP " + e.getStatusCode() + " - " + respBody);
        } catch (Exception e) {
            log.error("Gemini 调用失败: {}", e.getMessage());
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException("Gemini 调用失败：" + e.getMessage());
        }
    }

    /**
     * Anthropic Claude 专属调用
     */
    private String callClaude(AiConfig cfg, String systemPrompt, String userContent) {
        String url = cfg.getBaseUrl();
        if (!url.endsWith("/")) url += "/";
        String endpoint = url + "messages";

        Map<String, Object> body = Map.of(
            "model", cfg.getModelName().isBlank() ? "claude-3-5-sonnet-20241022" : cfg.getModelName(),
            "max_tokens", 8192,
            "system", systemPrompt,
            "messages", List.of(Map.of("role", "user", "content", userContent))
        );

        try {
            RestClient client = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", cfg.getApiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();

            String response = client.post().body(body).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.at("/type").asText("").equals("error")) {
                throw new RuntimeException(root.at("/error/message").asText("Claude 发生未知错误"));
            }
            return root.at("/content/0/text").asText("Claude 没有返回有效内容");
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String respBody = e.getResponseBodyAsString();
            try {
                JsonNode errRoot = objectMapper.readTree(respBody);
                 if (errRoot.at("/type").asText("").equals("error")) {
                    throw new RuntimeException(errRoot.at("/error/message").asText(e.getStatusText()));
                 }
            } catch (Exception parseEx) {}
            throw new RuntimeException("Claude API 错误: HTTP " + e.getStatusCode() + " - " + respBody);
        } catch (Exception e) {
            log.error("Claude 调用失败: {}", e.getMessage());
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException("Claude 调用失败：" + e.getMessage());
        }
    }

    /**
     * 统一调用分发
     */
    private String call(String systemPrompt, String userContent) {
        AiConfig cfg = resolveConfig();
        if (cfg == null || cfg.getBaseUrl() == null || cfg.getApiKey() == null) {
            return "【提示】当前用户尚未配置 AI 模型，请前往 AI 设置页面进行配置。";
        }
        String provider = cfg.getProvider() == null ? "openai" : cfg.getProvider().toLowerCase();
        return switch (provider) {
            case "gemini" -> callGemini(cfg, systemPrompt + "\n" + userContent);
            case "claude" -> callClaude(cfg, systemPrompt, userContent);
            // openai / qianwen / zhipu / deepseek / ollama / custom 均走 OpenAI 兼容
            default -> chatCompletions(cfg, systemPrompt, userContent);
        };
    }

    // =========================================================================
    // AIService 接口实现
    // =========================================================================

    @Override
    public String generateSummary(String content) {
        String text = content.replaceAll("<[^>]*>", "").substring(0, Math.min(content.length(), 2000));
        return call(
            "你是一位专业的内容编辑，请用中文为以下文章生成一段 80~120 字的简洁摘要，突出核心观点。",
            text
        );
    }

    @Override
    public List<String> suggestTitles(String title, String content) {
        String result = call(
            "你是资深媒体标题写作专家，请为以下文章生成 3 个吸引眼球的标题。" +
            "格式要求：每个标题占一行，不要编号，不要额外说明。",
            "当前标题：" + title + "\n正文摘要：" + content.replaceAll("<[^>]*>", "").substring(0, Math.min(content.length(), 500))
        );
        String[] lines = result.split("\n");
        return Arrays.stream(lines).map(String::trim).filter(s -> !s.isBlank()).limit(3).toList();
    }

    @Override
    public List<String> extractTags(String content) {
        String result = call(
            "请从以下文章中提取 5 个最相关的 SEO 标签关键词，每行一个，不要编号。",
            content.replaceAll("<[^>]*>", "").substring(0, Math.min(content.length(), 1000))
        );
        return Arrays.stream(result.split("\n")).map(String::trim).filter(s -> !s.isBlank()).limit(5).toList();
    }

    @Override
    public String extractCategory(String content, String categories) {
        String result = call(
            "你是一个自动分类助手。请从以下分类列表中，为提供的文章选出一个最合适的分类。\n" +
            "可选分类：" + categories + "\n" +
            "要求：\n" +
            "1. 只能从【可选分类】中挑选一个分类返回。\n" +
            "2. 不能有任何其他的解释说明或标点符号。\n" +
            "3. 如果都不匹配，返回可选分类中的第一个分类。",
            content.replaceAll("<[^>]*>", "").substring(0, Math.min(content.length(), 1500))
        );
        return result.trim();
    }

    @Override
    public String generateFullArticle(String topic, String outline) {
        return call(
            "你是一位资深的新闻媒体主编，擅长基于热点素材进行深度二次创作（洗稿改写）。" +
            "请根据提供的【参考素材/原文】，以【文章主题】为核心，撰写一篇全新的 HTML 格式资讯文章。" +
            "要求：\n" +
            "1. 严禁原文照抄，必须重新组织语言逻辑，通过不同的切入点进行叙述；\n" +
            "2. 严格紧凑的排版：只允许使用 <h2> 和 <p> 标签。标签之间绝对不允许有任何换行符 (\\n) 或空行。必须像紧连着的代码一样输出，例如：<p>段落一</p><h2>小标题</h2><p>段落二</p>；\n" +
            "3. 内容不少于 500 字，包含背景、现状、深度分析和总结，保持专业媒体风格；\n" +
            "4. 只返回干净的纯 HTML 字符串，不要有 markdown 代码块标记，不要加任何前导或后缀文字。",
            "文章主题：" + topic + "\n参考素材/原文内容：" + outline
        );
    }

    @Override
    public String autoMatchImage(String keyword) {
        // 图片匹配沿用 Unsplash，AI 模型不擅长返回图片链接
        String encoded = java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
        return "https://source.unsplash.com/800x400/?" + encoded;
    }

    @Override
    public String polishContent(String content) {
        return call(
            "你是专业的中文内容润色专家。请对以下文章进行深度润色：" +
            "1. 优化语句表达，使其更加专业流畅；" +
            "2. 增强阅读冲击力和感染力；" +
            "3. 保持原有 HTML 标签结构不变；" +
            "4. 只返回润色后的完整 HTML 内容，不要加任何说明。",
            content
        );
    }

    @Override
    public List<String> matchParagraphImages(String content) {
        // 提取关键词用于图片匹配，沿用 Unsplash
        String text = content.replaceAll("<[^>]*>", "");
        return List.of(
            "https://source.unsplash.com/800x450/?office,professional",
            "https://source.unsplash.com/800x450/?technology,innovation",
            "https://source.unsplash.com/800x450/?news,media"
        );
    }

    @Override
    public String scrapeAndAnalyze(String url, String extractionPrompt) {
        if (interactiveBrowserService == null) {
            throw new IllegalStateException("InteractiveBrowserService is not available in direct config mode.");
        }

        // 1. 无头浏览器获取带状态的可视全文本
        String rawText = interactiveBrowserService.getRenderedText(url);

        if (rawText == null || rawText.isBlank()) {
            throw new RuntimeException("未能从网页提取到任何有效文本");
        }
        
        // 截断超长文本（防止超出大模型上下文，取前 20000 字符，大概 10K Tokens）
        if (rawText.length() > 20000) {
            rawText = rawText.substring(0, 20000);
            log.warn("网页文本超长，已截断至 20000 字符");
        }

        // 2. 利用大模型强大的指令跟随和 RAG 理解能力进行结构化提取
        String systemPrompt = "你是一个顶级的万能网页数据解析引擎（Agentic Scraper）。\n" +
            "用户会提供一段从复杂网页环境直接抓取下来的无结构的、排版混乱的纯可见文本。\n" +
            "你的任务是：根据用户的提取要求，从这些无底部的杂乱文本中精准揪出目标数据，并严格输出合规的 JSON 格式。\n" +
            "【绝对要求】：\n" +
            "1. 你的回答必须只能是一个有效的 JSON，不能包含任何 markdown 代码块标识符（如 ```json 等），不加任何解释文字。\n" +
            "2. 如果提取不到，返回空的 JSON 数组 [] 或空对象 {}。";

        String userPrompt = "【提取要求】：\n" + extractionPrompt + 
            "\n\n【网页抓取原文（可能极度混乱）】：\n" + rawText;

        return call(systemPrompt, userPrompt);
    }
}
