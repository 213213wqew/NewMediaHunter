package com.news.publish.service.impl;

import com.news.publish.model.entity.Platform;
import com.news.publish.repository.PlatformRepository;
import com.news.publish.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformServiceImpl implements PlatformService {
    private final PlatformRepository platformRepository;

    @Override
    public List<Platform> getAllPlatforms() {
        return platformRepository.findAll();
    }

    @Override
    public Platform getPlatformById(Long id) {
        return platformRepository.findById(id).orElse(null);
    }
}
