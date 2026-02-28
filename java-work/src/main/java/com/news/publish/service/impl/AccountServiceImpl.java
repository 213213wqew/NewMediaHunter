package com.news.publish.service.impl;

import com.news.publish.model.entity.Account;
import com.news.publish.repository.AccountRepository;
import com.news.publish.service.AccountService;
import com.news.publish.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;

    @Override
    public List<Account> getAllAccounts() {
        if (UserContext.isAdmin()) {
            return accountRepository.findAll();
        }
        return accountRepository.findByUserId(UserContext.getUserId());
    }

    @Override
    public Account saveAccount(Account account) {
        if (account.getId() == null) {
            account.setUserId(UserContext.getUserId());
        } else {
            Account existing = accountRepository.findById(account.getId()).orElseThrow();
            if (!UserContext.isAdmin() && !existing.getUserId().equals(UserContext.getUserId())) {
                throw new RuntimeException("无权操作此账号");
            }
            account.setUserId(existing.getUserId());
        }

        // 优化：对敏感信息进行加密处理 (此处演示使用 Base64，实战建议使用 AES)
        if (account.getAppSecret() != null && !account.getAppSecret().isEmpty()) {
            if (!isBase64(account.getAppSecret())) {
                account.setAppSecret(java.util.Base64.getEncoder().encodeToString(account.getAppSecret().getBytes()));
            }
        }
        
        return accountRepository.save(account);
    }

    private boolean isBase64(String str) {
        try {
            java.util.Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void deleteAccount(Long id) {
        accountRepository.deleteById(id);
    }
}
