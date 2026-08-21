package com.app.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadAndDownloadShouldRoundTripBytes() throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(tempDir);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                "hello world".getBytes(StandardCharsets.UTF_8));

        String fileId = service.upload(file);

        assertNotNull(fileId);
        assertEquals("hello world", new String(service.download(fileId), StandardCharsets.UTF_8));
        assertEquals(true, Files.exists(tempDir.resolve(fileId)));
    }
}
