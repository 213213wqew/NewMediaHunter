package com.news.publish.controller;

import com.news.publish.model.entity.SensitiveWord;
import com.news.publish.service.ComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/compliance/words")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final ComplianceService complianceService;

    @GetMapping
    public List<SensitiveWord> getAll() {
        return complianceService.getAllWords();
    }

    @PostMapping
    public SensitiveWord add(@RequestBody SensitiveWord word) {
        return complianceService.addWord(word);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        complianceService.deleteWord(id);
    }
}
