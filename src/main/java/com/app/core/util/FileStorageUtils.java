package com.app.core.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class FileStorageUtils {

    /**
     * Generates a unique filename: userId_YYYYMMDDHHMMSS_8RandomChars.extension
     * @param userId The user's ID (Long)
     * @param originalFilename The original name of the file
     * @return A uniquely formatted filename string
     */
    public String generateUniqueFileName(Long userId, String originalFilename) {
        String userIdStr = String.valueOf(userId);
        return generateUniqueFileName(userIdStr, originalFilename);
    }
    
    public String generateUniqueFileName(String userIdStr, String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomId = UUID.randomUUID().toString().substring(0, 8);
        return userIdStr + "_" + timestamp + "_" + randomId + extension;
    }

    /**
     * Helper method to safely extract the file extension from a filename.
     * Returns the extension with the dot (e.g., ".jpg").
     */
    public String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex);
    }
}