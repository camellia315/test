package com.campus.lostfound.config;

import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "qiniu", name = "enabled", havingValue = "true")
public class QiniuConfig {

    @Value("${qiniu.access-key}")
    private String accessKey;

    @Value("${qiniu.secret-key}")
    private String secretKey;

    @Value("${qiniu.region:huanan}")
    private String regionName;

    @Bean
    public Auth auth() {
        return Auth.create(accessKey, secretKey);
    }

    @Bean
    public UploadManager uploadManager() {
        return new UploadManager(new com.qiniu.storage.Configuration(resolveRegion(regionName)));
    }

    @Bean
    public BucketManager bucketManager(Auth auth) {
        return new BucketManager(auth, new com.qiniu.storage.Configuration(resolveRegion(regionName)));
    }

    private Region resolveRegion(String region) {
        if (region == null) {
            return Region.huanan();
        }
        switch (region.toLowerCase()) {
            case "huadong":
                return Region.huadong();
            case "huabei":
                return Region.huabei();
            case "beimei":
                return Region.beimei();
            case "xinjiapo":
                return Region.xinjiapo();
            case "huanan":
            default:
                return Region.huanan();
        }
    }
}
