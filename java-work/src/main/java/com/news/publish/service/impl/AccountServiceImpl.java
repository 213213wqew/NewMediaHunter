package com.news.publish.service.impl;

import com.news.publish.model.entity.Account;
import com.news.publish.service.AccountFileStorage;
import com.news.publish.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 账号服务：回归本地文件存储。
 */
@Primary
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountFileStorage accountFileStorage;

    @Override
    public List<Account> getAllAccounts() {
        return accountFileStorage.findAll();
    }

    @Override
    public Optional<Account> getById(Long id) {
        return accountFileStorage.findById(id);
    }

    @Override
    public Account saveAccount(Account account) {
        if (account.getId() == null) {
            account.onCreate();
        } else {
            Account existing = accountFileStorage.findById(account.getId()).orElse(null);
            if (existing != null) {
                if (account.getCreateTime() == null) account.setCreateTime(existing.getCreateTime());
                if (account.getUserId() == null) account.setUserId(existing.getUserId());
            }
            account.onUpdate();
        }
        return accountFileStorage.save(account);
    }

    @Override
    public void deleteAccount(Long id) {
        accountFileStorage.deleteById(id);
    }
}
