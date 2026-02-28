package com.news.publish.model.dto;

import lombok.Data;
import java.util.Map;
import java.util.List;

@Data
public class PublishStats {
    private long totalAccounts;
    private long totalArticles;
    private long totalTasks;
    private double successRate;
    
    // 按日期统计的发布数 (近7天)
    // Key: 2024-03-20, Value: 15
    private List<ChartData> seriesData;
    
    // 各平台占比
    // Key: baijiahao, Value: 20
    private List<ChartData> platformData;

    @Data
    public static class ChartData {
        private String name;
        private Long value;
        
        public ChartData(String name, Long value) {
            this.name = name;
            this.value = value;
        }
    }
}
