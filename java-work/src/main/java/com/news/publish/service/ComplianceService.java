package com.news.publish.service;

import com.news.publish.model.entity.Article;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final com.news.publish.repository.SensitiveWordRepository sensitiveWordRepository;
    private final java.util.List<String> sensitiveWordsCache = new java.util.concurrent.CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        refreshWords();
    }

    /**
     * 从数据库刷新敏感词缓存
     */
    public void refreshWords() {
        java.util.List<String> words = sensitiveWordRepository.findAll().stream()
                .map(com.news.publish.model.entity.SensitiveWord::getWord)
                .collect(java.util.stream.Collectors.toList());
        sensitiveWordsCache.clear();
        sensitiveWordsCache.addAll(words);
        log.info("敏感词库已刷新，当前词量: {}", sensitiveWordsCache.size());
    }

    /**
     * 检查文章内容是否合规
     * @return 返回违规词列表，如果为空则表示合规
     */
    public List<String> checkContent(Article article) {
        String content = (article.getContent() != null ? article.getContent() : "") 
                       + (article.getTitle() != null ? article.getTitle() : "");
        
        // 如果缓存为空（可能还未初始化），则尝试执行一次刷新
        if (sensitiveWordsCache.isEmpty()) {
            refreshWords();
        }

        List<String> foundWords = sensitiveWordsCache.stream()
                .filter(content::contains)
                .collect(Collectors.toList());
        
        if (!foundWords.isEmpty()) {
            log.warn("发现合规风险: 标题={}, 命中词={}", article.getTitle(), foundWords);
        }
        return foundWords;
    }
}
