package com.news.publish.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.entity.AiWritingSpecification;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AiSpecJsonStorageService {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String DATA_DIR = "data";
    private final String JSON_FILE_PATH = DATA_DIR + "/ai_specs.json";

    public AiSpecJsonStorageService(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        // 注册 JavaTimeModule 避免 LocalDateTime 序列化失败
        this.objectMapper.registerModule(new JavaTimeModule());
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() throws IOException {
        Path dataDir = Paths.get(DATA_DIR);
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        File jsonFile = new File(JSON_FILE_PATH);
        if (!jsonFile.exists()) {
            // 初始化系统预设
            Resource resource = resourceLoader.getResource("classpath:ai_presets.json");
            List<AiWritingSpecification> presets = objectMapper.readValue(resource.getInputStream(),
                    new TypeReference<List<AiWritingSpecification>>(){});
            // 确保所有的 presets 都是系统级并拥有合法 ID
            long idCounter = System.currentTimeMillis();
            for (AiWritingSpecification p : presets) {
                p.setId(idCounter++);
                p.setIsSystem(true);
                p.prePersist();
            }
            saveAll(presets);
        }
    }

    private List<AiWritingSpecification> getAll() {
        File jsonFile = new File(JSON_FILE_PATH);
        if (!jsonFile.exists()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(jsonFile, new TypeReference<List<AiWritingSpecification>>(){});
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void saveAll(List<AiWritingSpecification> specs) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(JSON_FILE_PATH), specs);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("读取或写入 JSON 失败", e);
        }
    }

    public List<AiWritingSpecification> getList() {
        return getAll();
    }

    public List<AiWritingSpecification> getList(String category) {
        if (category == null || "ALL".equals(category)) {
            return getAll();
        }
        return getAll().stream().filter(s -> category.equals(s.getCategory())).collect(Collectors.toList());
    }

    public AiWritingSpecification save(AiWritingSpecification spec) {
        List<AiWritingSpecification> list = getAll();
        
        // 如果设置为该分类下的默认
        if (Boolean.TRUE.equals(spec.getIsDefault())) {
            list.forEach(s -> {
                if (s.getCategory() != null && s.getCategory().equals(spec.getCategory())) {
                    s.setIsDefault(false);
                }
            });
        }

        if (spec.getId() != null) {
            // 更新
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(spec.getId())) {
                    AiWritingSpecification exist = list.get(i);
                    // 保护系统预设不被随意篡改核心逻辑
                    if (Boolean.TRUE.equals(exist.getIsSystem())) {
                        throw new RuntimeException("系统预设不允许修改");
                    }
                    spec.setIsSystem(false);
                    spec.setCreatedAt(exist.getCreatedAt());
                    spec.preUpdate();
                    list.set(i, spec);
                    saveAll(list);
                    return spec;
                }
            }
        }

        // 新增
        spec.setId(System.currentTimeMillis());
        spec.setIsSystem(false); // 用户自定义的不可能是系统级
        spec.prePersist();
        list.add(spec);
        saveAll(list);
        return spec;
    }

    public void delete(Long id) {
        List<AiWritingSpecification> list = getAll();
        Optional<AiWritingSpecification> target = list.stream().filter(s -> s.getId().equals(id)).findFirst();
        if (target.isPresent()) {
            if (Boolean.TRUE.equals(target.get().getIsSystem())) {
                throw new RuntimeException("系统预设不允许删除");
            }
            list.removeIf(s -> s.getId().equals(id));
            saveAll(list);
        }
    }

    public void setDefault(Long id) {
        List<AiWritingSpecification> list = getAll();
        Optional<AiWritingSpecification> target = list.stream().filter(s -> s.getId().equals(id)).findFirst();
        if (target.isPresent()) {
            AiWritingSpecification spec = target.get();
            list.forEach(s -> {
                if (s.getCategory() != null && s.getCategory().equals(spec.getCategory())) {
                    s.setIsDefault(false);
                }
            });
            spec.setIsDefault(true);
            saveAll(list);
        }
    }

    public void restoreDefaults() throws IOException {
        // 保留原有的用户自定义数据，还原或更新系统级数据
        List<AiWritingSpecification> list = getAll();
        List<AiWritingSpecification> customSpecs = list.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsSystem()))
                .collect(Collectors.toList());

        Resource resource = resourceLoader.getResource("classpath:ai_presets.json");
        List<AiWritingSpecification> presets = objectMapper.readValue(resource.getInputStream(),
                new TypeReference<List<AiWritingSpecification>>(){});
        
        long idCounter = System.currentTimeMillis();
        for (AiWritingSpecification p : presets) {
            p.setId(idCounter++);
            p.setIsSystem(true);
            p.prePersist();
            customSpecs.add(p);
        }
        
        saveAll(customSpecs);
    }
}
