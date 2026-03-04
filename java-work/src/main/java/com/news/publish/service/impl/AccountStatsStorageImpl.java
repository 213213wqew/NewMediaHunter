package com.news.publish.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.publish.model.dto.AccountStatsDto;
import com.news.publish.service.AccountStatsStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountStatsStorageImpl implements AccountStatsStorage {

    private static final String DIR = ".news-publisher";
    private static final String FILE = "account_stats.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Path filePath;

    @PostConstruct
    public void init() {
        String base = System.getProperty("user.home");
        Path dir = Paths.get(base, DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建目录失败: {}", dir, e);
        }
        filePath = dir.resolve(FILE);
    }

    private List<AccountStatsDto> loadList() {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(filePath);
            AccountStatsDto[] arr = objectMapper.readValue(json, AccountStatsDto[].class);
            return arr == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(arr));
        } catch (Exception e) {
            log.warn("读取昨日数据文件失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveList(List<AccountStatsDto> list) {
        if (filePath == null) return;
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), list);
        } catch (IOException e) {
            log.error("保存昨日数据失败", e);
            throw new RuntimeException("保存昨日数据失败: " + e.getMessage());
        }
    }

    @Override
    public Map<Long, AccountStatsDto> findAll() {
        return loadList().stream()
                .filter(s -> s.getAccountId() != null)
                .collect(Collectors.toMap(AccountStatsDto::getAccountId, s -> s, (a, b) -> b));
    }

    @Override
    public Optional<AccountStatsDto> findByAccountId(Long accountId) {
        if (accountId == null) return Optional.empty();
        return loadList().stream()
                .filter(s -> accountId.equals(s.getAccountId()))
                .findFirst();
    }

    @Override
    public void save(AccountStatsDto stats) {
        if (stats == null || stats.getAccountId() == null) return;
        List<AccountStatsDto> list = new ArrayList<>(loadList());
        list.removeIf(s -> stats.getAccountId().equals(s.getAccountId()));
        list.add(stats);
        saveList(list);
    }

    @Override
    public void saveAll(List<AccountStatsDto> list) {
        if (list == null || list.isEmpty()) return;
        Map<Long, AccountStatsDto> map = list.stream()
                .filter(s -> s.getAccountId() != null)
                .collect(Collectors.toMap(AccountStatsDto::getAccountId, s -> s, (a, b) -> b));
        saveList(new ArrayList<>(map.values()));
    }
}
