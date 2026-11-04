package com.app.upload.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.app.storage.factory.StorageFactory;
import com.app.upload.repository.UploadChunkRepository;

@ExtendWith(MockitoExtension.class)
class ChunkServiceChecksumTest {

    @Mock
    private ChunkUploadStateService stateService;

    @Mock
    private UploadChunkRepository chunkRepository;

    @Mock
    private StorageFactory storageFactory;

    @Mock
    private MultipartFile file;

    @Test
    void rejectsChunkWhenChecksumDoesNotMatchBeforeClaimingIt() throws Exception {
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenReturn(
                new ByteArrayInputStream("actual chunk".getBytes(StandardCharsets.UTF_8)));

        ChunkService service = new ChunkService(stateService, chunkRepository, storageFactory);

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadChunk("upload-id", 0, file, "user@example.com", "0".repeat(64)));

        verify(stateService, never()).claimChunk(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
