package com.news.publish.controller;

import com.news.publish.model.entity.SensitiveWord;
import com.news.publish.repository.SensitiveWordRepository;
import com.news.publish.service.ComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/compliance/words")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordRepository repository;
    private final ComplianceService complianceService;

    @GetMapping
    public List<SensitiveWord> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public SensitiveWord add(@RequestBody SensitiveWord word) {
        SensitiveWord saved = repository.save(word);
        complianceService.refreshWords(); // 刷新缓存
        return saved;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
        complianceService.refreshWords(); // 刷新缓存
    }
}
