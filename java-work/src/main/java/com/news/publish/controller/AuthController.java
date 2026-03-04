package com.news.publish.controller;

import com.news.publish.model.dto.LoginRequest;
import com.news.publish.model.dto.LoginResponse;
import com.news.publish.model.dto.SaveSessionRequest;
import com.news.publish.service.AuthService;
import com.news.publish.service.DesktopSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final DesktopSessionService desktopSessionService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** 桌面版：保存会话到本机，下次启动可恢复 */
    @PostMapping("/save-session")
    public void saveSession(@RequestBody SaveSessionRequest body) {
        if (body != null && body.getToken() != null) {
            desktopSessionService.saveSession(body.getToken(), body.getUsername(), body.getRole());
        }
    }

    /** 桌面版：从本机恢复会话（WebView 重启后 localStorage 为空，用此接口恢复） */
    @GetMapping("/restore-session")
    public DesktopSessionService.SessionData restoreSession() {
        return desktopSessionService.restoreSession();
    }

    /** 桌面版：清除本机会话（登出或 token 失效时调用，避免下次启动恢复无效 token） */
    @PostMapping("/clear-session")
    public void clearSession() {
        desktopSessionService.clearSession();
    }
}
