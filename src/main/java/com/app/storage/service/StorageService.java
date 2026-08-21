package com.app.storage.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String upload(MultipartFile file);
    
    String upload(byte[] data, String fileName);

    byte[] download(String fileId);
}