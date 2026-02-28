package com.news.publish.service.impl;

import com.news.publish.model.dto.LoginRequest;
import com.news.publish.model.dto.LoginResponse;
import com.news.publish.model.entity.SysUser;
import com.news.publish.repository.SysUserRepository;
import com.news.publish.service.AuthService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserRepository userRepository;
    
    // 简单内存 Token 存储，生产环境应使用 Redis 或 JWT
    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();

    @PostConstruct
    @Override
    public void initRoles() {
        if (userRepository.count() == 0) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword("123456");
            admin.setRole("ADMIN");
            userRepository.save(admin);

            SysUser user = new SysUser();
            user.setUsername("user1");
            user.setPassword("123456");
            user.setRole("USER");
            userRepository.save(user);
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
                
        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.put(token, user.getId());

        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

    @Override
    public SysUser getUserByToken(String token) {
        if (token == null) return null;
        Long userId = tokenStore.get(token);
        if (userId != null) {
            return userRepository.findById(userId).orElse(null);
        }
        return null;
    }
}
