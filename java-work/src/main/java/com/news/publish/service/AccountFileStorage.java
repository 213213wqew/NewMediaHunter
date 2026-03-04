package com.news.publish.service;

import com.news.publish.model.entity.Account;

import java.util.List;
import java.util.Optional;

/**
 * 账号本地文件存储：不依赖数据库，读写本地 JSON 文件。
 */
public interface AccountFileStorage {
    List<Account> findAll();
    Optional<Account> findById(Long id);
    Account save(Account account);
    void deleteById(Long id);
    /** 生成新 ID */
    Long nextId();
}
