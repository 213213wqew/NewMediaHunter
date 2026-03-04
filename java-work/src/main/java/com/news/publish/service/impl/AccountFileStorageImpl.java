package com.news.publish.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.publish.model.dto.AccountRecord;
import com.news.publish.model.entity.Account;
import com.news.publish.service.AccountFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountFileStorageImpl implements AccountFileStorage {

    private static final String DIR = ".news-publisher";
    private static final String FILE = "accounts.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Path filePath;
    private final AtomicLong nextIdHolder = new AtomicLong(1L);

    @PostConstruct
    public void init() {
        String base = System.getProperty("user.home");
        Path dir = Paths.get(base, DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建账号存储目录失败: {}", dir, e);
        }
        filePath = dir.resolve(FILE);
    }

    private AccountRecord load() {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            AccountRecord r = new AccountRecord();
            r.setNextId(1L);
            return r;
        }
        try {
            String json = Files.readString(filePath);
            AccountRecord r = objectMapper.readValue(json, AccountRecord.class);
            if (r.getAccounts() == null) r.setAccounts(new java.util.ArrayList<>());
            nextIdHolder.set(r.getNextId());
            return r;
        } catch (Exception e) {
            log.warn("读取账号文件失败，使用空列表: {}", e.getMessage());
            AccountRecord r = new AccountRecord();
            r.setNextId(nextIdHolder.get());
            return r;
        }
    }

    private void save(AccountRecord record) {
        if (filePath == null) return;
        try {
            record.setNextId(nextIdHolder.get());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), record);
        } catch (IOException e) {
            log.error("保存账号文件失败", e);
            throw new RuntimeException("保存账号失败: " + e.getMessage());
        }
    }

    private static Account toEntity(AccountRecord.Item item) {
        Account a = new Account();
        a.setId(item.getId());
        a.setPlatformId(item.getPlatformId());
        a.setAccountName(item.getAccountName());
        a.setCookieData(item.getCookieData());
        a.setStatus(item.getStatus() != null ? item.getStatus() : 1);
        a.setUserId(1L);
        return a;
    }

    private static AccountRecord.Item toItem(Account a, String platformKey) {
        AccountRecord.Item item = new AccountRecord.Item();
        item.setId(a.getId());
        item.setPlatformId(a.getPlatformId());
        item.setPlatformKey(platformKey);
        item.setAccountName(a.getAccountName());
        item.setCookieData(a.getCookieData());
        item.setStatus(a.getStatus() != null ? a.getStatus() : 1);
        return item;
    }

    private static AccountRecord.Item toItem(Account a) {
        return toItem(a, null);
    }

    @Override
    public List<Account> findAll() {
        AccountRecord record = load();
        return record.getAccounts().stream()
                .map(AccountFileStorageImpl::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Account> findById(Long id) {
        if (id == null) return Optional.empty();
        return load().getAccounts().stream()
                .filter(i -> id.equals(i.getId()))
                .findFirst()
                .map(AccountFileStorageImpl::toEntity);
    }

    @Override
    public Account save(Account account) {
        AccountRecord record = load();
        if (account.getId() == null) {
            long id = nextIdHolder.getAndIncrement();
            account.setId(id);
            AccountRecord.Item item = toItem(account);
            record.getAccounts().add(item);
        } else {
            record.getAccounts().removeIf(i -> account.getId().equals(i.getId()));
            record.getAccounts().add(toItem(account));
        }
        save(record);
        return account;
    }

    @Override
    public void deleteById(Long id) {
        AccountRecord record = load();
        record.getAccounts().removeIf(i -> id.equals(i.getId()));
        save(record);
    }

    @Override
    public Long nextId() {
        return nextIdHolder.getAndIncrement();
    }
}
