package com.news.publish.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.publish.model.entity.AiConfig;
import com.news.publish.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import com.news.publish.service.AIService;
import com.news.publish.service.impl.RealAIServiceImpl;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/ai-config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AIService aiService;

    // DTO for internal agent requests from Python bridge
    public static class AiAgentRequest {
        public String prompt;
        public String htmlContext;
    }

    /** 获取当前用户的 AI 配置（不存在则返回 null） */
    @GetMapping
    public AiConfig getConfig() {
        return aiConfigService.getConfig();
    }

    /** 保存（新增/更新）当前用户的 AI 配置 */
    @PostMapping
    public AiConfig saveConfig(@RequestBody AiConfig config) {
        return aiConfigService.saveConfig(config);
    }

    /** 测试 AI 配置连接（使用传入的临时配置，不落库） */
    @PostMapping("/test")
    public Map<String, Object> testConfig(@RequestBody AiConfig config) {
        try {
            AIService testService = new RealAIServiceImpl(config);
            String result = testService.generateSummary("这是一段用于测试 AI 模型连接是否正常的内容。");
            return Map.of("success", true, "message", "连接成功！模型响应：" + result.substring(0, Math.min(result.length(), 80)));
        } catch (Exception e) {
            return Map.of("success", false, "message", "连接失败：" + e.getMessage());
        }
    }

    /**
     * 内部接口：供 Python Agent (Skills) 调用，在面临页面改版无法定位元素时，
     * 传入崩溃时的 HTML 上下文和问题，由大模型分析后返回全新的 CSS 选择器或解决方案。
     */
    @PostMapping("/internal/ask-ai")
    public Map<String, Object> askAiFromAgent(@RequestBody AiAgentRequest request) {
        try {
            // 此处借用现有 AIService 的直接对话能力 (RealAIServiceImpl.askAgentQuestion)
            String answer = ((RealAIServiceImpl) aiService)
                    .askAgentQuestion(request.prompt, request.htmlContext);
            return Map.of("success", true, "answer", answer);
        } catch (Exception e) {
            log.error("内部代理 Agent 请求大模型失败: {}", e.getMessage());
            return Map.of("success", false, "message", "AI请求失败：" + e.getMessage());
        }
    }

    /**
     * 动态拉取提供商支持的模型列表
     * 前端传入 baseUrl、apiKey、provider，后端代理拉取（解决 CORS 问题）
     */
    @PostMapping("/list-models")
    public Map<String, Object> listModels(@RequestBody AiConfig config) {
        try {
            List<String> models = fetchModels(config);
            return Map.of("models", models);
        } catch (Exception e) {
            log.warn("获取模型列表失败: {}", e.getMessage());
            return Map.of("models", List.of(), "error", e.getMessage());
        }
    }

    private List<String> fetchModels(AiConfig config) throws Exception {
        String provider = config.getProvider() == null ? "openai" : config.getProvider().toLowerCase();
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) throw new RuntimeException("Base URL 不能为空");
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        // Gemini 使用独立的模型列表 API
        if ("gemini".equals(provider)) {
            String endpoint = baseUrl + "models?key=" + config.getApiKey();
            String resp = RestClient.create().get().uri(endpoint).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(resp);
            List<String> result = new ArrayList<>();
            JsonNode models = root.path("models");
            if (models.isArray()) {
                for (JsonNode m : models) {
                    String name = m.path("name").asText();
                    if (!name.isBlank()) result.add(name.replace("models/", ""));
                }
            }
            return result;
        }

        // OpenAI 兼容格式（openai/qianwen/zhipu/deepseek/moonshot/siliconflow/ollama 等）
        String endpoint = baseUrl + "models";
        String resp = RestClient.builder()
            .baseUrl(endpoint)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
            .get().retrieve().body(String.class);

        JsonNode root = objectMapper.readTree(resp);
        List<String> result = new ArrayList<>();
        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode m : data) {
                String id = m.path("id").asText();
                if (!id.isBlank()) result.add(id);
            }
        }
        // 按字母排序便于查找
        Collections.sort(result);
        return result;
    }
}

