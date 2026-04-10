package com.campus.lostfound.controller;

import com.campus.common.api.ApiResponse;
import com.campus.lostfound.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final FileStorageService fileStorageService;

    @Value("${qiniu.prefix.product:product/}")
    private String productPrefix;

    @Value("${qiniu.prefix.lost-found:lost-found/}")
    private String lostFoundPrefix;

    @Value("${qiniu.prefix.avatar:avatar/}")
    private String avatarPrefix;

    @Value("${qiniu.prefix.activity:activity/}")
    private String activityPrefix;

    @Value("${upload.local-dir:uploads}")
    private String localDir;

    public UploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/image")
    public ApiResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "type", defaultValue = "product") String type) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail(400, "文件不能为空");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            return ApiResponse.fail(400, "图片大小不能超过5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.fail(400, "只能上传图片文件");
        }

        try {
            String prefix = getPrefix(type);
            String imageUrl = fileStorageService.uploadImage(file, prefix);
            Map<String, String> result = new HashMap<>();
            result.put("url", imageUrl);
            result.put("name", file.getOriginalFilename());
            return ApiResponse.success(result);
        } catch (Exception ex) {
            return ApiResponse.fail(500, "图片上传失败: " + ex.getMessage());
        }
    }

    @PostMapping("/images")
    public ApiResponse<Map<String, Object>> uploadImages(@RequestParam("files") MultipartFile[] files,
                                                         @RequestParam(value = "type", defaultValue = "product") String type) {
        if (files == null || files.length == 0) {
            return ApiResponse.fail(400, "文件不能为空");
        }
        if (files.length > 9) {
            return ApiResponse.fail(400, "最多上传9张图片");
        }
        for (MultipartFile file : files) {
            if (file.getSize() > 5L * 1024 * 1024) {
                return ApiResponse.fail(400, "单张图片不能超过5MB");
            }
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ApiResponse.fail(400, "只能上传图片文件");
            }
        }

        try {
            List<String> urls = fileStorageService.uploadImages(files, getPrefix(type));
            Map<String, Object> result = new HashMap<>();
            result.put("urls", urls);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            return ApiResponse.fail(500, "批量上传失败: " + ex.getMessage());
        }
    }

    @DeleteMapping("/image")
    public ApiResponse<Map<String, Object>> deleteImage(@RequestParam("url") String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return ApiResponse.fail(400, "图片地址不能为空");
        }
        try {
            fileStorageService.deleteImage(imageUrl);
            return ApiResponse.success(Map.of("deleted", true));
        } catch (Exception ex) {
            return ApiResponse.fail(500, "图片删除失败: " + ex.getMessage());
        }
    }

    @GetMapping("/local")
    public ResponseEntity<Resource> localPreview(@RequestParam("key") String key) {
        try {
            Path baseDir = Paths.get(localDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(key).normalize();
            if (!filePath.startsWith(baseDir) || !Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (contentType != null) {
                mediaType = MediaType.parseMediaType(contentType);
            }
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String getPrefix(String type) {
        if (!StringUtils.hasText(type)) {
            return productPrefix;
        }
        switch (type) {
            case "product":
                return productPrefix;
            case "lost-found":
                return lostFoundPrefix;
            case "avatar":
                return avatarPrefix;
            case "activity":
                return activityPrefix;
            default:
                return "other/";
        }
    }
}
