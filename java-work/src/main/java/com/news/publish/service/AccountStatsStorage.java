package com.news.publish.service;

import com.news.publish.model.dto.AccountStatsDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 账号昨日数据本地文件存储，与 accounts.json 分离。
 */
public interface AccountStatsStorage {
    Map<Long, AccountStatsDto> findAll();
    Optional<AccountStatsDto> findByAccountId(Long accountId);
    void save(AccountStatsDto stats);
    void saveAll(List<AccountStatsDto> list);
}
