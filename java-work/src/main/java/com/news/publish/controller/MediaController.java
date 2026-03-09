package com.news.publish.controller;

import com.news.publish.model.entity.MediaResource;
import com.news.publish.service.MediaResourceFileStorage;
import com.news.publish.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaResourceFileStorage mediaRepository;

    @GetMapping("/list")
    public List<MediaResource> listAll(@RequestParam(required = false) String type) {
        if (UserContext.isAdmin()) {
            if (type != null && !type.isEmpty()) {
                return mediaRepository.findByFileType(type);
            }
            return mediaRepository.findAll();
        } else {
            Long userId = UserContext.getUserId();
            if (type != null && !type.isEmpty()) {
                return mediaRepository.findByUserIdAndFileType(userId, type);
            }
            return mediaRepository.findByUserId(userId);
        }
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        // Find existing to check permissions
        Optional<MediaResource> opt = mediaRepository.findAll().stream().filter(m -> id.equals(m.getId())).findFirst();
        if (opt.isEmpty()) throw new RuntimeException("资源不存在");
        MediaResource resource = opt.get();
        // 鉴权优化：允许管理员删除，或允许所有者删除。若无所有者（老数据），也允许当前用户删除。
        if (!UserContext.isAdmin() && resource.getUserId() != null && !resource.getUserId().equals(UserContext.getUserId())) {
            throw new RuntimeException("无权删除此资源");
        }
        mediaRepository.deleteById(id);
    }

    @PostMapping("/batch-delete")
    public void batchDelete(@RequestBody java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        
        // 权限校验：如果不是管理员，只能删除自己的素材
        if (!UserContext.isAdmin()) {
            Long currentUserId = UserContext.getUserId();
            java.util.List<MediaResource> allResources = mediaRepository.findAll();
            for (Long id : ids) {
                java.util.Optional<MediaResource> res = allResources.stream().filter(m -> id.equals(m.getId())).findFirst();
                if (res.isPresent()) {
                    MediaResource m = res.get();
                    // 如果素材有明确的 owner 且不是当前用户，则拒绝
                    if (m.getUserId() != null && !m.getUserId().equals(currentUserId)) {
                        throw new RuntimeException("包含无权删除的资源: " + id);
                    }
                }
            }
        }
        
        mediaRepository.deleteByIds(ids);
    }

    @PostMapping("/delete-all")
    public void deleteAll() {
        Long userId = UserContext.isAdmin() ? null : UserContext.getUserId();
        mediaRepository.deleteAllByUserId(userId);
    }
}
