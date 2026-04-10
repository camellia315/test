package com.campus.lostfound.service.impl;

import com.campus.lostfound.service.FileStorageService;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "qiniu", name = "enabled", havingValue = "true")
public class QiniuFileStorageService implements FileStorageService {

    private final Auth auth;
    private final UploadManager uploadManager;
    private final BucketManager bucketManager;

    @Value("${qiniu.bucket}")
    private String bucket;

    @Value("${qiniu.domain}")
    private String domain;

    public QiniuFileStorageService(Auth auth, UploadManager uploadManager, BucketManager bucketManager) {
        this.auth = auth;
        this.uploadManager = uploadManager;
        this.bucketManager = bucketManager;
    }

    @Override
    public String uploadImage(MultipartFile file, String prefix) throws IOException {
        String key = normalizePrefix(prefix) + generateFileName(file.getOriginalFilename());
        String token = auth.uploadToken(bucket);

        Response response = uploadManager.put(file.getBytes(), key, token);
        if (!response.isOK()) {
            throw new RuntimeException("七牛云上传失败: " + response.bodyString());
        }
        return normalizeDomain(domain) + "/" + key;
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
        if (!StringUtils.hasText(imageUrl)) {
            return;
        }
        String normalizedDomain = normalizeDomain(domain);
        String key = imageUrl;
        if (imageUrl.startsWith(normalizedDomain + "/")) {
            key = imageUrl.substring((normalizedDomain + "/").length());
        }
        bucketManager.delete(bucket, key);
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
        if (!safe.isEmpty() && !safe.endsWith("/")) {
            safe += "/";
        }
        return safe;
    }

    private String normalizeDomain(String value) {
        if (!StringUtils.hasText(value)) {
            throw new RuntimeException("七牛云域名未配置");
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
