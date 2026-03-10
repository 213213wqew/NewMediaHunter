package com.news.publish.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.SensitiveWord;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private static final String DATA_DIR = "data";
    private static final String WORDS_FILE = "sensitive_words.json";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // 内存中的敏感词字典对象列表
    private final List<SensitiveWord> wordStore = new ArrayList<>();
    
    // 用于快速比对的高效字符串缓存
    private final java.util.List<String> sensitiveWordsCache = new java.util.concurrent.CopyOnWriteArrayList<>();

    private Path getFilePath() {
        return Paths.get(System.getProperty("user.dir"), DATA_DIR, WORDS_FILE);
    }

    @PostConstruct
    public void init() {
        Path dir = Paths.get(System.getProperty("user.dir"), DATA_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建数据目录失败: {}", dir, e);
        }
        refreshWords();
    }

    private synchronized void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(getFilePath().toFile(), wordStore);
        } catch (IOException e) {
            log.error("保存敏感词 JSON 失败", e);
        }
    }

    /**
     * 从本地 JSON 刷新敏感词缓存
     */
    public synchronized void refreshWords() {
        Path filePath = getFilePath();
        wordStore.clear();

        if (Files.exists(filePath)) {
            try {
                List<SensitiveWord> words = objectMapper.readValue(filePath.toFile(), new TypeReference<List<SensitiveWord>>() {});
                if (words != null) {
                    wordStore.addAll(words);
                }
            } catch (Exception e) {
                log.error("读取 {} 失败: {}", WORDS_FILE, e.getMessage());
            }
        } else {
            // 文件不存在时，初始化为空数组持久化到硬盘
            saveToFile();
        }

        sensitiveWordsCache.clear();
        for (SensitiveWord sw : wordStore) {
            if (sw.getWord() != null) {
                sensitiveWordsCache.add(sw.getWord());
            }
        }
        log.info("本地敏感词库已挂载，当前词量: {}", sensitiveWordsCache.size());
    }

    // --- 对外提供的 CRUD 操作 ---

    public List<SensitiveWord> getAllWords() {
        return new ArrayList<>(wordStore);
    }

    public synchronized SensitiveWord addWord(SensitiveWord word) {
        // 分配自增ID
        long maxId = wordStore.stream().mapToLong(w -> w.getId() == null ? 0 : w.getId()).max().orElse(0L);
        word.setId(maxId + 1);
        
        if (word.getCreateTime() == null) {
            word.setCreateTime(LocalDateTime.now());
        }

        // 检查重复
        boolean exists = wordStore.stream().anyMatch(w -> w.getWord().equals(word.getWord()));
        if (!exists) {
            wordStore.add(word);
            saveToFile();
            refreshWords();
        }
        return word;
    }

    public synchronized void deleteWord(Long id) {
        boolean removed = wordStore.removeIf(w -> id.equals(w.getId()));
        if (removed) {
            saveToFile();
            refreshWords();
        }
    }

    /**
     * 检查文章内容是否合规
     * @return 返回违规词列表，如果为空则表示合规
     */
    public List<String> checkContent(Article article) {
        String content = (article.getContent() != null ? article.getContent() : "") 
                       + (article.getTitle() != null ? article.getTitle() : "");
        
        if (sensitiveWordsCache.isEmpty() && !wordStore.isEmpty()) {
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
