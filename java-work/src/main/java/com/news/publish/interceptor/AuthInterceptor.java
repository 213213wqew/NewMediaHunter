package com.news.publish.interceptor;

import com.news.publish.model.entity.SysUser;
import com.news.publish.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/login") || uri.startsWith("/api/auth/save-session")
                || uri.startsWith("/api/auth/restore-session") || uri.startsWith("/api/auth/clear-session")
                || uri.startsWith("/api/file/download/")) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || token.isEmpty()) {
            response.setStatus(401);
            response.getWriter().write("Unauthorized: No token provided");
            return false;
        }

        SysUser user = authService.getUserByToken(token);
        if (user == null) {
            response.setStatus(401);
            response.getWriter().write("Unauthorized: Invalid token");
            return false;
        }

        UserContext.setUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
