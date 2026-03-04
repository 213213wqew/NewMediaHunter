package com.news.publish.service;

import com.news.publish.model.entity.Account;

import java.util.List;
import java.util.Optional;

public interface AccountService {
    List<Account> getAllAccounts();
    Optional<Account> getById(Long id);
    Account saveAccount(Account account);
    void deleteAccount(Long id);
}
