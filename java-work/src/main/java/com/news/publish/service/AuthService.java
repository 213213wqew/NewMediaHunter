package com.news.publish.service;

import com.news.publish.model.dto.LoginRequest;
import com.news.publish.model.dto.LoginResponse;
import com.news.publish.model.entity.SysUser;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    SysUser getUserByToken(String token);
    void initRoles(); // 初始化 admin 和 test 用户
}
