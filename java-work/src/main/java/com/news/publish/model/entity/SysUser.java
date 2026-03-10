package com.news.publish.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysUser {
    private Long id;
    private String username;
    private String password;

    /**
     * Role: "ADMIN" or "USER"
     */
    private String role = "USER";

    private LocalDateTime createTime = LocalDateTime.now();
}
