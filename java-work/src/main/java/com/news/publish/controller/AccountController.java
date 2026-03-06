package com.news.publish.controller;

import com.news.publish.model.dto.AccountStatsDto;
import com.news.publish.model.dto.BindStartRequest;
import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Platform;
import com.news.publish.repository.PlatformRepository;
import com.news.publish.service.AccountService;
import com.news.publish.service.AccountStatsStorage;
import com.news.publish.service.automation.PythonSkillRunner;
import com.news.publish.service.automation.SkillDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;
    private final AccountStatsStorage accountStatsStorage;
    private final SkillDiscoveryService skillDiscoveryService;
    private final PythonSkillRunner pythonSkillRunner;
    private final PlatformRepository platformRepository;

    @GetMapping("/list")
    public List<Account> listAccounts() {
        return accountService.getAllAccounts();
    }

    /** 获取各账号昨日数据（粉丝、阅读、收益），用于账号管理页展示 */
    @GetMapping("/stats")
    public Map<Long, AccountStatsDto> getStats() {
        return accountStatsStorage.findAll();
    }

    /**
     * 更新数据：对选中的已绑定账号调用平台技能拉取昨日数据并写入本地文件。
     * 若请求体带 accountIds 则只更新这些账号；否则更新全部。
     */
    @PostMapping("/refresh-stats")
    public Map<Long, AccountStatsDto> refreshStats(@RequestBody(required = false) Map<String, Object> body) {
        List<Account> all = accountService.getAllAccounts();
        Set<Long> filterIds = null;
        if (body != null && body.get("accountIds") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Number> ids = (List<Number>) body.get("accountIds");
            if (ids != null && !ids.isEmpty()) {
                filterIds = ids.stream().map(Number::longValue).collect(Collectors.toSet());
            }
        }
        final Set<Long> idsToRefresh = filterIds;
        List<Account> accounts = idsToRefresh == null ? all : all.stream().filter(a -> idsToRefresh.contains(a.getId())).collect(Collectors.toList());
        Map<Long, Account> accountMap = new HashMap<>();
        for (Account a : accounts) {
            if (a.getCookieData() == null || a.getCookieData().isBlank()) continue;
            Platform platform = platformRepository.findById(a.getPlatformId()).orElse(null);
            if (platform == null) continue;
            String skillId = platform.getPlatformKey() + "_agent";
            if (skillDiscoveryService.getSkill(skillId) == null) continue;
            accountMap.put(a.getId(), a);
        }
        List<Account> toRefresh = new ArrayList<>(accountMap.values());
        if (toRefresh.isEmpty()) {
            return accountStatsStorage.findAll();
        }
        // 多线程并发拉取各账号昨日数据，结果放入 map，最后合并写入一次
        Map<Long, AccountStatsDto> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = toRefresh.stream()
            .map(acc -> CompletableFuture.runAsync(() -> {
                Platform platform = platformRepository.findById(acc.getPlatformId()).orElse(null);
                if (platform == null) return;
                String platformKey = platform.getPlatformKey();
                SkillDiscoveryService.SkillMetadata meta = skillDiscoveryService.getSkill(platformKey + "_agent");
                if (meta == null) return;
                Map<String, Object> params = new HashMap<>();
                params.put("command", "FETCH_STATS");
                params.put("accountId", String.valueOf(acc.getId()));
                params.put("platform", platformKey);
                params.put("cookieJson", acc.getCookieData());
                params.put("headless", true);
                try {
                    PythonSkillRunner.SkillExecutionResult result = pythonSkillRunner.execute(meta, params);
                    if (!result.isSuccess() || result.getData() == null) return;
                    Map<String, Object> data = result.getData();
                    AccountStatsDto dto = new AccountStatsDto();
                    dto.setAccountId(acc.getId());
                    dto.setTotalFans(_int(data.get("totalFans")));
                    dto.setTotalReads(_long(data.get("totalReads")));
                    dto.setTotalRevenue(data.get("totalRevenue") != null ? data.get("totalRevenue").toString() : "");
                    dto.setYesterdayFans(_int(data.get("yesterdayFans")));
                    dto.setYesterdayReads(_long(data.get("yesterdayReads")));
                    dto.setYesterdayRevenue(data.get("yesterdayRevenue") != null ? data.get("yesterdayRevenue").toString() : "");
                    dto.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    results.put(acc.getId(), dto);
                } catch (Exception e) {
                    log.warn("拉取账号 {} 昨日数据失败: {}", acc.getId(), e.getMessage());
                }
            }, taskExecutor))
            .collect(Collectors.toList());
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // 合并当前已有数据，用本次拉取结果覆盖对应账号，一次性写入
        Map<Long, AccountStatsDto> current = accountStatsStorage.findAll();
        current.putAll(results);
        accountStatsStorage.saveAll(new ArrayList<>(current.values()));
        return accountStatsStorage.findAll();
    }

    private static int _int(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString().replace(",", "")); } catch (Exception e) { return 0; }
    }

    private static long _long(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString().replace(",", "")); } catch (Exception e) { return 0L; }
    }

    /**
     * 开始绑定：仅需平台+账号名称，弹出对应平台登录页，登录成功后 Token 写入本地文件，弹窗关闭。
     * 不依赖数据库，全部本地文件存储。
     */
    @PostMapping("/bind-start")
    public Account bindStart(@RequestBody BindStartRequest request) {
        if (request.getPlatformKey() == null || request.getPlatformKey().isBlank()) {
            throw new RuntimeException("请选择平台");
        }
        if (request.getAccountName() == null || request.getAccountName().isBlank()) {
            throw new RuntimeException("请输入账号名称");
        }
        Platform platform = platformRepository.findByPlatformKey(request.getPlatformKey().trim())
                .orElseThrow(() -> new RuntimeException("未找到该平台: " + request.getPlatformKey()));
        String skillId = request.getPlatformKey().trim() + "_agent";
        SkillDiscoveryService.SkillMetadata meta = skillDiscoveryService.getSkill(skillId);
        if (meta == null) {
            throw new RuntimeException("该平台暂不支持扫码绑定: " + request.getPlatformKey());
        }
        String accountId = "bind_" + System.currentTimeMillis();
        Map<String, Object> params = new HashMap<>();
        params.put("command", "BIND_LOGIN");
        params.put("accountId", accountId);
        params.put("platform", request.getPlatformKey().trim());
        PythonSkillRunner.SkillExecutionResult result = pythonSkillRunner.execute(meta, params);
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getMessage() != null ? result.getMessage() : "登录未完成或超时");
        }
        Object cookieJson = result.getData() != null ? result.getData().get("cookieJson") : null;
        if (cookieJson == null || cookieJson.toString().isBlank()) {
            throw new RuntimeException("未获取到登录凭证，请重试");
        }
        Account account = new Account();
        account.setPlatformId(platform.getId());
        account.setAccountName(request.getAccountName().trim());
        account.setCookieData(cookieJson.toString());
        account.setStatus(1);
        account.setUserId(1L);
        return accountService.saveAccount(account);
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
