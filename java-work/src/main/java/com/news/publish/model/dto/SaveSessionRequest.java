package com.news.publish.model.dto;

import lombok.Data;

@Data
public class SaveSessionRequest {
    private String token;
    private String username;
    private String role;
}
