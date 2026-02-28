package com.news.publish.controller;

import com.news.publish.model.entity.Account;
import com.news.publish.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/list")
    public List<Account> listAccounts() {
        return accountService.getAllAccounts();
    }

    @PostMapping("/save")
    public Account saveAccount(@RequestBody Account account) {
        return accountService.saveAccount(account);
    }

    @DeleteMapping("/{id}")
    public void deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
    }
}
