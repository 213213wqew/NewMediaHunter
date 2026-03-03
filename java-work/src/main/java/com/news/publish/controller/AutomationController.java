package com.news.publish.controller;

import com.news.publish.service.automation.InteractiveBrowserService;
import com.news.publish.service.automation.SkillDiscoveryService;
import com.news.publish.service.automation.PythonSkillRunner;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
public class AutomationController {

    private final InteractiveBrowserService interactiveBrowserService;
    private final SkillDiscoveryService skillDiscoveryService;
    private final PythonSkillRunner pythonSkillRunner;

    /**
     * 开启一个发布会话
     */
    @PostMapping("/start")
    public Result start(@RequestBody StartRequest request) {
        String sessionId = interactiveBrowserService.startSession(request.getUrl(), request.getCookieJson());
        return Result.success(sessionId);
    }

    /**
     * 获取最新截图
     */
    @GetMapping("/snapshot/{sessionId}")
    public Result snapshot(@PathVariable String sessionId) {
        String base64 = interactiveBrowserService.captureScreenshot(sessionId);
        if (base64 == null) return Result.error("会话不存在");
        return Result.success(base64);
    }

    /**
     * 执行动作 (填充或点击)
     */
    @PostMapping("/action")
    public Result action(@RequestBody ActionRequest request) {
        if ("fill".equals(request.getType())) {
            interactiveBrowserService.fillField(request.getSessionId(), request.getSelector(), request.getValue());
        } else if ("click".equals(request.getType())) {
            if (request.getX() != null && request.getY() != null) {
                interactiveBrowserService.clickAtPoint(request.getSessionId(), request.getX(), request.getY());
            } else {
                interactiveBrowserService.clickElement(request.getSessionId(), request.getSelector());
            }
        }
        return Result.success();
    }

    /**
     * 获取所有可用的独立技能
     */
    @GetMapping("/skills")
    public Result getSkills() {
        return Result.success(skillDiscoveryService.getAvailableSkills());
    }

    /**
     * 刷新技能目录
     */
    @PostMapping("/skills/refresh")
    public Result refreshSkills() {
        skillDiscoveryService.refreshSkills();
        return Result.success();
    }

    /**
     * 执行指定的独立 Python 技能
     */
    @PostMapping("/skills/execute/{skillId}")
    public Result executeSkill(@PathVariable String skillId, @RequestBody Map<String, Object> params) {
        SkillDiscoveryService.SkillMetadata meta = skillDiscoveryService.getSkill(skillId);
        if (meta == null) return Result.error("技能不存在: " + skillId);
        
        PythonSkillRunner.SkillExecutionResult result = pythonSkillRunner.execute(meta, params);
        if (result.isSuccess()) {
            return Result.success(result);
        } else {
            return Result.error(result.getMessage());
        }
    }

    /**
     * 关闭会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Result close(@PathVariable String sessionId) {
        interactiveBrowserService.closeSession(sessionId);
        return Result.success();
    }

    @Data
    public static class StartRequest {
        private String url;
        private String cookieJson;
    }

    @Data
    public static class ActionRequest {
        private String sessionId;
        private String type; // fill, click
        private String selector;
        private String value;
        private Integer x;
        private Integer y;
    }

    @Data
    public static class BaijiahaoPublishRequest {
        private String title;
        private String htmlContent;
        private String category;
        private String cookieJson;
        private boolean draft;
    }

    @Data
    public static class Result {
        private int code;
        private String msg;
        private Object data;

        public static Result success() {
            return success(null);
        }

        public static Result success(Object data) {
            Result result = new Result();
            result.setCode(200);
            result.setData(data);
            return result;
        }

        public static Result error(String msg) {
            Result result = new Result();
            result.setCode(500);
            result.setMsg(msg);
            return result;
        }
    }
}
