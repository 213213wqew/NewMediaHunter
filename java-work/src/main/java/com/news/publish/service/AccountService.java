package com.news.publish.service;

import com.news.publish.model.entity.Account;
import java.util.List;

public interface AccountService {
    List<Account> getAllAccounts();
    Account saveAccount(Account account);
    void deleteAccount(Long id);
}
