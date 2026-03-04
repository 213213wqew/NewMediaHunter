package com.news.publish.model.dto;

import lombok.Data;

/**
 * 账号数据：总粉丝/总阅读/总收入 + 昨日粉丝/阅读/收益，用于账号管理页展示。
 */
@Data
public class AccountStatsDto {
    private Long accountId;
    private Integer totalFans;
    private Long totalReads;
    private String totalRevenue;
    private Integer yesterdayFans;
    private Long yesterdayReads;
    private String yesterdayRevenue;
    private String updatedAt;
}
