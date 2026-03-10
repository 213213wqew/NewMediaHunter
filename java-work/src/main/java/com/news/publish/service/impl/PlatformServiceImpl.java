package com.news.publish.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.entity.Platform;
import com.news.publish.service.PlatformService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformServiceImpl implements PlatformService {

    private static final String DATA_DIR = "data";
    private static final String PLATFORMS_FILE = "platforms.json";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final List<Platform> platformStore = new ArrayList<>();

    private Path getFilePath() {
        return Paths.get(System.getProperty("user.dir"), DATA_DIR, PLATFORMS_FILE);
    }

    @PostConstruct
    public void init() {
        Path dir = Paths.get(System.getProperty("user.dir"), DATA_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建数据目录失败: {}", dir, e);
        }

        Path filePath = getFilePath();
        if (Files.exists(filePath)) {
            try {
                List<Platform> list = objectMapper.readValue(filePath.toFile(), new TypeReference<List<Platform>>() {});
                if (list != null) {
                    platformStore.addAll(list);
                }
            } catch (Exception e) {
                log.error("读取 {} 失败: {}", PLATFORMS_FILE, e.getMessage());
            }
        }

        // 初始化默认常量平台
        if (platformStore.isEmpty()) {
            initDefaultPlatforms(filePath);
        }
    }

    private void initDefaultPlatforms(Path filePath) {
        LocalDateTime now = LocalDateTime.now();

        Platform baijia = new Platform();
        baijia.setId(1L);
        baijia.setPlatformKey("baijiahao");
        baijia.setPlatformName("百家号");
        baijia.setOfficialUrl("https://baijiahao.baidu.com/");
        baijia.setCreateTime(now);
        baijia.setUpdateTime(now);
        platformStore.add(baijia);

        Platform toutiao = new Platform();
        toutiao.setId(2L);
        toutiao.setPlatformKey("toutiao");
        toutiao.setPlatformName("头条号");
        toutiao.setOfficialUrl("https://mp.toutiao.com/");
        toutiao.setCreateTime(now);
        toutiao.setUpdateTime(now);
        platformStore.add(toutiao);

        Platform sohu = new Platform();
        sohu.setId(3L);
        sohu.setPlatformKey("sohu");
        sohu.setPlatformName("搜狐号");
        sohu.setOfficialUrl("https://mp.sohu.com/");
        sohu.setCreateTime(now);
        sohu.setUpdateTime(now);
        platformStore.add(sohu);

        Platform wxgzh = new Platform();
        wxgzh.setId(4L);
        wxgzh.setPlatformKey("wxgzh");
        wxgzh.setPlatformName("微信公众号");
        wxgzh.setOfficialUrl("https://mp.weixin.qq.com/");
        wxgzh.setCreateTime(now);
        wxgzh.setUpdateTime(now);
        platformStore.add(wxgzh);

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), platformStore);
            log.info("已生成默认平台列表到 {}", filePath);
        } catch (IOException e) {
            log.error("保存默认平台 JSON 失败", e);
        }
    }

    @Override
    public List<Platform> getAllPlatforms() {
        return new ArrayList<>(platformStore);
    }

    @Override
    public Platform getPlatformById(Long id) {
        if (id == null) return null;
        return platformStore.stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst()
                .orElse(null);
    }
}
