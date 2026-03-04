package com.news.publish.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 本地文件中的账号记录，用于 JSON 序列化 */
@Data
public class AccountRecord {
    private long nextId = 1L;
    private List<Item> accounts = new ArrayList<>();

    @Data
    public static class Item {
        private Long id;
        private Long platformId;
        private String platformKey;
        private String accountName;
        private String cookieData;
        private Integer status;
    }
}
