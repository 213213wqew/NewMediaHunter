package com.news.publish.controller;

import com.news.publish.model.entity.AiWritingSpecification;
import com.news.publish.service.AiSpecJsonStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-spec")
public class AiWritingSpecificationController {

    @Autowired
    private AiSpecJsonStorageService storageService;

    @GetMapping("/list")
    public List<AiWritingSpecification> list(@RequestParam(required = false) String category) {
        return storageService.getList(category);
    }

    @PostMapping("/save")
    public AiWritingSpecification save(@RequestBody AiWritingSpecification spec) {
        return storageService.save(spec);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        storageService.delete(id);
    }

    @PostMapping("/set-default/{id}")
    public void setDefault(@PathVariable Long id) {
        storageService.setDefault(id);
    }

    @GetMapping("/presets")
    public List<AiWritingSpecification> getPresets() {
        return storageService.getList(); // 现在 list 已经包含预设，如果前端还需要单独获取可以这里复用
    }

    @PostMapping("/init-defaults")
    public void initDefaults() throws Exception {
        storageService.restoreDefaults();
    }
}
