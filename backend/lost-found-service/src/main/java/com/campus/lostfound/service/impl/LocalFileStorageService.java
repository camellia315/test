package com.campus.lostfound.service.impl;

import com.campus.lostfound.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "qiniu", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final String LOCAL_VIEW_PATH = "/api/upload/local?key=";

    @Value("${upload.local-dir:uploads}")
    private String localDir;

    @Override
    public String uploadImage(MultipartFile file, String prefix) throws IOException {
        String normalizedPrefix = normalizePrefix(prefix);
        String filename = generateFileName(file.getOriginalFilename());
        String key = normalizedPrefix + filename;

        Path baseDir = Paths.get(localDir).toAbsolutePath().normalize();
        Path targetPath = baseDir.resolve(key).normalize();
        if (!targetPath.startsWith(baseDir)) {
            throw new RuntimeException("文件路径非法");
        }

        Files.createDirectories(targetPath.getParent());
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return LOCAL_VIEW_PATH + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    @Override
    public List<String> uploadImages(MultipartFile[] files, String prefix) throws IOException {
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            urls.add(uploadImage(file, prefix));
        }
        return urls;
    }

    @Override
    public void deleteImage(String imageUrl) throws Exception {
        String key = parseKeyFromImageUrl(imageUrl);
        if (!StringUtils.hasText(key)) {
            return;
        }
        Path baseDir = Paths.get(localDir).toAbsolutePath().normalize();
        Path target = baseDir.resolve(key).normalize();
        if (target.startsWith(baseDir)) {
            Files.deleteIfExists(target);
        }
    }

    private String parseKeyFromImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        int idx = imageUrl.indexOf("key=");
        if (idx < 0) {
            return null;
        }
        String encoded = imageUrl.substring(idx + 4);
        int amp = encoded.indexOf('&');
        if (amp >= 0) {
            encoded = encoded.substring(0, amp);
        }
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private String generateFileName(String originalFilename) {
        String suffix = ".jpg";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString().replace("-", "") + suffix;
    }

    private String normalizePrefix(String prefix) {
        String safe = StringUtils.hasText(prefix) ? prefix.trim() : "";
        if (safe.startsWith("/")) {
            safe = safe.substring(1);
        }
        if (!safe.isEmpty() && !safe.endsWith("/")) {
            safe += "/";
        }
        return safe;
    }
}
