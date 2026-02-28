package com.news.publish.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class SysUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    /**
     * Role: "ADMIN" or "USER"
     */
    @Column(nullable = false)
    private String role = "USER";

    private LocalDateTime createTime = LocalDateTime.now();
}
