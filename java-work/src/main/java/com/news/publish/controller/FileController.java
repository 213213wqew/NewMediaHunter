package com.news.publish.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final String uploadDir = "uploads/";
    private final com.news.publish.repository.MediaResourceRepository mediaRepository;

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        
        if (file.isEmpty()) {
            result.put("errno", 1);
            result.put("message", "文件为空");
            return result;
        }

        try {
            // 确保目录存在
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String fileName = UUID.randomUUID().toString() + extension;
            
            Path path = Paths.get(uploadDir + fileName);
            Files.write(path, file.getBytes());

            log.info("文件上传成功: {}", fileName);

            // 记录到数据库
            com.news.publish.model.entity.MediaResource resource = new com.news.publish.model.entity.MediaResource();
            resource.setOriginalUrl(originalFilename);
            resource.setPlatformMediaUrl("/api/file/view/" + fileName);
            resource.setFileType(isVideoButton(extension) ? "video" : "image");
            resource.setUploadStatus(1); // 已上传本地
            mediaRepository.save(resource);

            result.put("errno", 0);
            Map<String, String> data = new HashMap<>();
            data.put("url", resource.getPlatformMediaUrl()); 
            data.put("alt", originalFilename);
            result.put("data", data);
            
        } catch (IOException e) {
            log.error("文件上传失败", e);
            result.put("errno", 1);
            result.put("message", "服务器写入错误");
        }

        return result;
    }

    @GetMapping("/view/{fileName}")
    public byte[] view(@PathVariable String fileName) throws IOException {
        Path path = Paths.get(uploadDir + fileName);
        return Files.readAllBytes(path);
    }

    private boolean isVideoButton(String ext) {
        String e = ext.toLowerCase();
        return e.equals(".mp4") || e.equals(".mov") || e.equals(".avi") || e.equals(".wmv");
    }
}
