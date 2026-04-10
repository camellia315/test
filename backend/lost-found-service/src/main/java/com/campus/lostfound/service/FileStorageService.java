package com.campus.lostfound.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface FileStorageService {
    String uploadImage(MultipartFile file, String prefix) throws IOException;

    List<String> uploadImages(MultipartFile[] files, String prefix) throws IOException;

    void deleteImage(String imageUrl) throws Exception;
}
