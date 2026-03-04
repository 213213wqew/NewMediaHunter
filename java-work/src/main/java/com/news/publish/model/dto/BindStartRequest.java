package com.news.publish.model.dto;

import lombok.Data;

@Data
public class BindStartRequest {
    /** 平台标识，如 baijiahao、toutiao */
    private String platformKey;
    /** 账号昵称 */
    private String accountName;
}
