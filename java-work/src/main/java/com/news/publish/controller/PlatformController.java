package com.news.publish.controller;

import com.news.publish.model.entity.Platform;
import com.news.publish.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformService platformService;

    @GetMapping("/list")
    public List<Platform> listPlatforms() {
        return platformService.getAllPlatforms();
    }

    @GetMapping("/{id}")
    public Platform getPlatform(@PathVariable Long id) {
        return platformService.getPlatformById(id);
    }
}
