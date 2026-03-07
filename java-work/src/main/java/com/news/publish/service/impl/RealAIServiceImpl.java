package com.news.publish.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.publish.model.entity.AiConfig;
import com.news.publish.model.entity.AiWritingSpecification;

import com.news.publish.service.AIService;
import com.news.publish.service.AiConfigService;
import com.news.publish.service.automation.ArticleExtractorService;
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
    private final ArticleExtractorService articleExtractorService;
    private final com.news.publish.service.AiSpecJsonStorageService specificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------ 用于测试连接时的临时实例（direct config，不依赖 Spring 上下文）------
    private final AiConfig directConfig;

    /** Spring 容器注入 —— 正常业务流程使用 */
    @org.springframework.beans.factory.annotation.Autowired
    public RealAIServiceImpl(AiConfigService aiConfigService, 
                            @org.springframework.context.annotation.Lazy InteractiveBrowserService interactiveBrowserService,
                            ArticleExtractorService articleExtractorService,
                            com.news.publish.service.AiSpecJsonStorageService specificationService) {
        this.aiConfigService = aiConfigService;
        this.interactiveBrowserService = interactiveBrowserService;
        this.articleExtractorService = articleExtractorService;
        this.specificationService = specificationService;
        this.directConfig = null;
    }

    /** 临时实例 —— 仅用于 /api/ai-config/test 接口 */
    public RealAIServiceImpl(AiConfig directConfig) {
        this.aiConfigService = null;
        this.interactiveBrowserService = null;
        this.articleExtractorService = null;
        this.specificationService = null;
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
    // -------- 获取指定的写作规范提示词 --------
    private String resolveSystemPrompt(String category, Long specId, String defaultPrompt) {
        if (specificationService == null) return defaultPrompt;
        
        // 1. 如果传了 specId，优先查指定的
        if (specId != null) {
            return specificationService.getList().stream()
                .filter(s -> s.getId().equals(specId))
                .map(AiWritingSpecification::getPromptContent)
                .findFirst()
                .orElse(defaultPrompt);
        }
        
        // 2. 如果没传，查当前用户的默认规范
        return specificationService.getList(category).stream()
            .filter(s -> Boolean.TRUE.equals(s.getIsDefault()))
            .map(AiWritingSpecification::getPromptContent)
            .findFirst()
            .orElse(defaultPrompt);
    }

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
        String text = content.replaceAll("<[^>]*>", "");
        int len = Math.min(text.length(), 2000);
        return call(
            "你是一位专业的内容编辑，请用中文为以下文章生成一段 80~120 字的简洁摘要，突出核心观点。",
            text.substring(0, len)
        );
    }

    @Override
    public List<String> suggestTitles(String title, String content) {
        String text = content.replaceAll("<[^>]*>", "");
        int len = Math.min(text.length(), 500);
        String result = call(
            "你是资深媒体标题写作专家，请为以下文章生成 3 个吸引眼球的标题。" +
            "格式要求：每个标题占一行，不要编号，不要额外说明。",
            "当前标题：" + title + "\n正文摘要：" + text.substring(0, len)
        );
        String[] lines = result.split("\n");
        return Arrays.stream(lines).map(String::trim).filter(s -> !s.isBlank()).limit(3).toList();
    }

    @Override
    public List<String> extractTags(String content) {
        String text = content.replaceAll("<[^>]*>", "");
        int len = Math.min(text.length(), 1000);
        String result = call(
            "请从以下文章中提取 5 个最相关的 SEO 标签关键词，每行一个，不要编号。",
            text.substring(0, len)
        );
        return Arrays.stream(result.split("\n")).map(String::trim).filter(s -> !s.isBlank()).limit(5).toList();
    }

    @Override
    public String extractCategory(String content, String categories) {
        String text = content.replaceAll("<[^>]*>", "");
        int len = Math.min(text.length(), 1500);
        String result = call(
            "你是一个自动分类助手。请从以下分类列表中，为提供的文章选出一个最合适的分类。\n" +
            "可选分类：" + categories + "\n" +
            "要求：\n" +
            "1. 只能从【可选分类】中挑选一个分类返回。\n" +
            "2. 不能有任何其他的解释说明或标点符号。\n" +
            "3. 如果都不匹配，返回可选分类中的第一个分类。",
            text.substring(0, len)
        );
        return result.trim();
    }

    @Override
    public String generateFullArticle(String topic, String outline, Long specId) {
        String defaultSystem = "你是一个资深的新闻自媒体主编，擅长撰写吸引读者点击、具有爆款潜质、且排版极度精美的资讯文稿。\n" +
            "请根据提供的【参考素材/原文历史】，以【文章主题】为核心，撰写一篇全新的 HTML 格式资讯文章。" +
            "要求：\n" +
            "1. 标题保持：尽量保留提供的【文章主题】核心语意作为标题；\n" +
            "2. 严禁原文照抄正文，必须重新组织语言逻辑，通过不同的切入点进行叙述；\n" +
            "3. 严格紧凑的排版：只允许使用 <h2> 和 <p> 标签。标签之间绝对不允许有任何换行符 (\\n) 或空行。必须像紧连着的代码一样输出，例如：<p>段落一</p><h2>小标题</h2><p>段落二</p>；\n" +
            "4. 精简内容：总字数控制在 260 到 350 字左右，语言要干练，突出核心要点即可；\n" +
            "5. **图片插入/配图准则（最高优先级）**：\n" +
            "   - **优先使用原图**：素材中包含 `【图片素材N 开始】URL【图片素材N 结束】`。你**必须**将其转换为标准 HTML：`<img src=\"URL\" />` 插入合适位置。**注意：对于此类原图，严禁添加 data-ai-rebuild 属性。**\n" +
            "   - **AI 补位配图**：如果你认为素材原图质量太差、有严重水印，或者素材中根本没有图片，你必须自拟 1 张配图描述，并使用占位格式：\n" +
            "     `<img data-ai-rebuild=\"true\" data-prompt=\"A detailed English descriptive prompt\" src=\"https://images.unsplash.com/photo-1504711432869-0fd30f78bb14\" />`。\n" +
            "   - **【强制要求】**：`data-prompt` 属性必须使用英文描述。整篇文章必须至少包含 1 张图片标签。\n" +
            "6. 只返回干净的纯 HTML 字符串，不要有 markdown 代码块标记，不要加任何前导或后缀文字。";

        String systemPrompt = resolveSystemPrompt("GENERATION", specId, defaultSystem);
        
        String res = call(
            systemPrompt,
            "文章主题：" + topic + "\n参考素材/原文内容：" + outline
        );

        // --- 后端强制补救逻辑：如果 AI 依然没给图片，手动塞一张 ---
        if (res != null && !res.contains("<img")) {
            log.warn("AI 生成文章丢失图片标签，触发后端强制补录逻辑 (topic={})", topic);
            
            String fallbackImg = "";
            // 默认尝试从素材原图中提取
            String imageUrl = null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("【图片素材.*?开始】(https?://.*?)【图片素材.*?结束】").matcher(outline);
            if (m.find()) {
                imageUrl = m.group(1);
                log.info("从原始素材中成功捞回原图: {}", imageUrl);
                // 原图不需要重绘标记
                fallbackImg = String.format("<div style=\"text-align:center;margin:15px 0;\"><img src=\"%s\" style=\"max-width:100%%; border-radius:8px; display:inline-block;\" /></div>", imageUrl);
            } else {
                // 如果素材真的没图，再走 AI 匹配
                imageUrl = autoMatchImage(topic + " high quality cinematic news photography");
                log.info("素材无图，已通过 AI 引擎生成新图: {}", imageUrl);
                // AI 匹配图可以带上重绘标记以便前端进一步美化
                fallbackImg = String.format("<div style=\"text-align:center;margin:15px 0;\"><img data-ai-rebuild=\"true\" data-prompt=\"%s\" src=\"%s\" style=\"max-width:100%%; border-radius:8px; display:inline-block;\" /></div>", topic, imageUrl);
            }
            
            // 尝试插在第一个段落后面
            if (res.contains("</p>")) {
                res = res.replaceFirst("</p>", "</p>" + fallbackImg);
            } else {
                res = fallbackImg + res;
            }
        }

        return res;
    }

    @Override
    public String generateImage(String prompt) {
        AiConfig config = resolveConfig();
        if (config == null || config.getApiKey() == null) {
            log.warn("AI 绘图配置缺失，回退到免费图库模式");
            String fallbackUrl = autoMatchImage(prompt);
            log.info("免费图库匹配结果: {}", fallbackUrl);
            return fallbackUrl;
        }

        // --- 安全性闸门：仅当用户手动开启 AI 绘图时，才发起付费 API 请求 ---
        if (Boolean.FALSE.equals(config.getEnableAiImage())) {
            log.info("未开启 AI 绘图开关，系统自动为您切换为免费图库模式");
            String fallbackUrl = autoMatchImage(prompt);
            log.info("免费图库匹配结果: {}", fallbackUrl);
            return fallbackUrl;
        }

        try {
            // 目前主要对接 OpenAI DALL-E 3。如果 baseUrl 不是 OpenAI，可能需要适配
            String url = config.getBaseUrl().endsWith("/v1") ? config.getBaseUrl() + "/images/generations" : config.getBaseUrl() + "/v1/images/generations";
            
            Map<String, Object> body = Map.of(
                "model", "dall-e-3",
                "prompt", prompt,
                "n", 1,
                "size", "1024x1024"
            );

            log.info("AI 绘图请求 (Premium): description={}", prompt);
            JsonNode res = RestClient.create().post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

            if (res != null && res.has("data") && res.get("data").isArray() && !res.get("data").isEmpty()) {
                String imageUrl = res.get("data").get(0).get("url").asText();
                log.info("AI 绘图成功: {}", imageUrl);
                return imageUrl;
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nfe) {
            log.warn("当前 API 供应商不支持 /v1/images/generations 接口 (404)，已为您自动降级到免费图片模式");
        } catch (Exception e) {
            log.error("AI 绘图异常 (可能是余额足或权限问题): {}", e.getMessage());
        }
        
        String finalUrl = autoMatchImage(prompt);
        log.info("AI 绘图降级处理完成，最终 URL: {}", finalUrl);
        return finalUrl;
    }

    @Override
    public String autoMatchImage(String keyword) {
        // 方案：优化关键词提取，移除介词干扰，并去掉严格的 /all 限制，确保图片多样性
        try {
            // 1. 基础清理
            String raw = keyword.replaceAll("(?i)description[:=]", "")
                                .replaceAll("[^a-zA-Z\\s]", " ")
                                .toLowerCase()
                                .trim();
            
            // 2. 核心词提取：过滤掉常见的无意义虚词，只留关键名词/动词
            List<String> stopWords = List.of("a", "an", "the", "in", "on", "at", "with", "and", "or", "of", "for", "from", "to", "by", "is", "are", "was", "were", "there", "it", "this", "that");
            String[] words = raw.split("\\s+");
            StringBuilder refined = new StringBuilder();
            int count = 0;
            for (String w : words) {
                if (w.length() > 2 && !stopWords.contains(w)) {
                    if (refined.length() > 0) refined.append(",");
                    refined.append(w);
                    count++;
                    if (count >= 3) break; // 最多取3个核心词，搜索更精准且多样
                }
            }
            
            // 3. 兜底
            String tags = refined.length() > 0 ? refined.toString() : "news,politics";
            
            // 4. 纳秒种子生成 lock，强制刷新缓存
            long lock = System.nanoTime() % 1000000;
            
            // 修正：去掉 /all。/all 要求必须匹配所有标签，标签多了很容易因为匹配不到而返回一张默认的“猫图”
            // 现在改为模糊匹配模式，大大提升相关度，并解决“怎么都是一张图”的问题
            String finalImageUrl = "https://loremflickr.com/800/450/" + tags + "?lock=" + lock;
            
            log.info("【图源引擎】Refined Tags: {}, Final URL: {}", tags, finalImageUrl);
            return finalImageUrl;
        } catch (Exception e) {
            return "https://images.unsplash.com/photo-1504711432869-0fd30f78bb14?auto=format&fit=crop&w=800&q=80";
        }
    }

    @Override
    public String polishContent(String content, Long specId) {
        String defaultSystem = "你是专业的中文内容润色专家。请对以下文章进行深度润色：" +
            "1. 优化语句表达，使其更加专业流畅；" +
            "2. 增强阅读冲击力和感染力；" +
            "3. 保持原有 HTML 标签结构不变；" +
            "4. 只返回润色后的完整 HTML 内容，不要加任何说明。";
            
        return call(resolveSystemPrompt("POLISH", specId, defaultSystem), content);
    }

    @Override
    public List<String> matchParagraphImages(String content) {
        // 修正：彻底解决插图推荐重复且不符的问题
        // 方案：直接调用 AI 绘图描述生成接口，为文章生成三张专属的示意图
        String stripped = content.replaceAll("<[^>]*>", "").trim();
        if (stripped.length() > 500) stripped = stripped.substring(0, 500);
        
        // 提取文章标题或前两句作为生成依据
        String kw = stripped.length() > 50 ? stripped.substring(0, 50) : stripped;
        
        return List.of(
            autoMatchImage(kw + " cinemetic real photo high quality"),
            autoMatchImage(kw + " detail news photography"),
            autoMatchImage(kw + " wide angle view press")
        );
    }

    @Override
    public String scrapeAndAnalyze(String url, String extractionPrompt) {
        if (interactiveBrowserService == null) {
            throw new IllegalStateException("InteractiveBrowserService is not available in direct config mode.");
        }

        // 1. 无头浏览器获取带状态的可视全文本
        String rawText = articleExtractorService.extractContent(url, null, null);

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

    @Override
    public List<String> matchHotTopics(String content, String hotTopicsJson) {
        String stripped = content.replaceAll("<[^>]*>", "");
        int len = Math.min(stripped.length(), 2000);
        
        String systemPrompt = "你是一个专业的内容分析助手，擅长分析文章并将其与当前热门任务/话题进行精准匹配。\n" +
            "【输入】：用户将提供文章正文，以及一个包含热门话题的 JSON 字符串列表（格式为 [{\"topic\": \"...\", \"participants\": \"...\"}, ...]）。\n" +
            "【任务】：请根据文章内容，从提供的话题 JSON 列表里挑选出 1~3 个最相关的、能增加曝光量的话题名称（必须带 # 符号，如 #热点#）。\n" +
            "【输出要求】：直接返回话题名称列表，每行一个。如果没有合适的匹配，返回空。绝对不要有任何多余的废话或解释。";
        
        String userPrompt = "【当前文章正文】：\n" + stripped.substring(0, len) + 
            "\n\n【可选热门话题列表】：\n" + hotTopicsJson;
            
        String result = call(systemPrompt, userPrompt);
        List<String> matched = Arrays.stream(result.split("\n")).map(String::trim).filter(s -> !s.isBlank()).limit(3).toList();
        log.info("AI 匹配热点结果: {}", matched);
        return matched;
    }

    /**
     * 为 Python 层 Agent 提供内部定位修复接口 (Self-Healing Locator)
     * @param prompt 诸如“查找发布按钮对应选择器”的要求
     * @param htmlContext UI 报错时的当前精简上下文 
     * @return Agent 需要的新执行逻辑或纯 CSS 选择器
     */
    public String askAgentQuestion(String prompt, String htmlContext) {
        String systemPrompt = "你是一个浏览器自动化 (RPA) 辅助智能体 (Agent Configurator)。\n" +
            "当自动化程序的 Playwright 选择器因为页面 UI 改版而失效时，Python 会把当前的 DOM 快照和出错步骤发给你。\n" +
            "你的任务是：根据 Python 提供的失败点和最新的 DOM，返回一个 **能够且仅能够** 用于 Playwright 的干净的 CSS 选择器或 text 定位表达式，" +
            "例如 'button.new-publish-btn'。绝对不要回复任何废话、Markdown 代码块 (` ``` `)、或者解释。脚本需要直接拿着你的输出结果放在 page.locator() 里运行。\n" +
            "如果没找到，回复 'Not Found'。";

        String userPrompt = "【失败的任务/要求】：\n" + prompt + 
            "\n\n【页面 DOM 快照】：\n" +
            (htmlContext != null && htmlContext.length() > 30000 ? htmlContext.substring(0, 30000) : htmlContext);
            
        return call(systemPrompt, userPrompt).trim();
    }
    @Override
    public String analyzeNews(String title, String content, Long specId) {
        String defaultSystem = "你是一个专业的新闻分析专家。请对提供的新闻内容进行深度解读。\n" +
            "要求输出如下 Markdown 格式：\n" +
            "💡 **核心价值**：一句话点出该新闻对读者的实际意义或潜在影响。\n" +
            "🚀 **启发思考**：提供 1-2 个基于该新闻的独特观点或对未来的预测。\n" +
            "📌 **场景标签**：提供 3 个相关的 #标签。";
            
        String systemPrompt = resolveSystemPrompt("SUMMARY", specId, defaultSystem);
            
        String userPrompt = "【新闻标题】：\n" + title + "\n\n【新闻正文】：\n" + 
            (content.length() > 5000 ? content.substring(0, 5000) : content);
            
        return call(systemPrompt, userPrompt);
    }
}
