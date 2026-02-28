package com.news.publish.controller;

import com.news.publish.model.entity.MediaResource;
import com.news.publish.repository.MediaResourceRepository;
import com.news.publish.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaResourceRepository mediaRepository;

    @GetMapping("/list")
    public List<MediaResource> listAll(@RequestParam(required = false) String type) {
        if (UserContext.isAdmin()) {
            if (type != null && !type.isEmpty()) {
                return mediaRepository.findByFileTypeOrderByCreateTimeDesc(type);
            }
            return mediaRepository.findAllByOrderByCreateTimeDesc();
        } else {
            Long userId = UserContext.getUserId();
            if (type != null && !type.isEmpty()) {
                return mediaRepository.findByUserIdAndFileTypeOrderByCreateTimeDesc(userId, type);
            }
            return mediaRepository.findByUserIdOrderByCreateTimeDesc(userId);
        }
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        MediaResource resource = mediaRepository.findById(id).orElseThrow(() -> new RuntimeException("资源不存在"));
        if (!UserContext.isAdmin() && !resource.getUserId().equals(UserContext.getUserId())) {
            throw new RuntimeException("无权删除此资源");
        }
        mediaRepository.deleteById(id);
    }
}
