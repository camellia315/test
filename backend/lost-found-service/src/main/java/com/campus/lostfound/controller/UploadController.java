package com.campus.lostfound.controller;

import com.campus.common.api.ApiResponse;
import com.campus.lostfound.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

    @Value("${upload.proxy.enabled:true}")
    private boolean proxyEnabled;

    @Value("${upload.proxy.allow-hosts:img.camellia315.xyz}")
    private String proxyAllowHosts;

    @Value("${upload.proxy.insecure-ssl:true}")
    private boolean proxyInsecureSsl;

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

    @GetMapping("/proxy")
    public ResponseEntity<Resource> proxyImage(@RequestParam("url") String remoteUrl) {
        if (!proxyEnabled) {
            return ResponseEntity.notFound().build();
        }
        if (!StringUtils.hasText(remoteUrl)) {
            return ResponseEntity.badRequest().build();
        }
        URI uri;
        try {
            uri = URI.create(remoteUrl.trim());
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return ResponseEntity.badRequest().build();
        }
        String host = uri.getHost();
        if (!isProxyHostAllowed(host)) {
            return ResponseEntity.status(403).build();
        }

        try {
            HttpURLConnection connection = openRemoteConnection(uri.toString());
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "image/*,*/*;q=0.8");
            connection.setRequestProperty("User-Agent", "CampusImageProxy/1.0");

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                return ResponseEntity.status(statusCode).build();
            }

            byte[] bytes;
            try (InputStream stream = connection.getInputStream()) {
                bytes = stream.readAllBytes();
            }
            if (bytes == null || bytes.length == 0) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            String contentType = connection.getContentType();
            if (StringUtils.hasText(contentType)) {
                try {
                    mediaType = MediaType.parseMediaType(contentType);
                } catch (Exception ignored) {
                }
            }
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                    .body(new ByteArrayResource(bytes));
        } catch (Exception ex) {
            return ResponseEntity.status(502).build();
        }
    }

    private boolean isProxyHostAllowed(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        Set<String> allowList = parseAllowHosts();
        if (allowList.contains(normalized)) {
            return true;
        }
        return allowList.stream().anyMatch(item -> item.startsWith(".") && normalized.endsWith(item));
    }

    private Set<String> parseAllowHosts() {
        String raw = StringUtils.hasText(proxyAllowHosts) ? proxyAllowHosts : "img.camellia315.xyz";
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private HttpURLConnection openRemoteConnection(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        if (connection instanceof HttpsURLConnection && proxyInsecureSsl) {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            HttpsURLConnection https = (HttpsURLConnection) connection;
            https.setSSLSocketFactory(sslContext.getSocketFactory());
            https.setHostnameVerifier((hostname, session) -> true);
        }
        return connection;
    }

    private String getPrefix(String type) {
        if (!StringUtils.hasText(type)) {
            return productPrefix;
        }
        switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "product":
            case "market":
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
