package com.app.upload.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.upload.entity.UploadJob;
import com.app.upload.repository.UploadJobRepository;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private UploadJobRepository jobRepository;

    @Mock
    private ChunkUploadStateService stateService;

    @Test
    void rejectsResumeWhenFingerprintDoesNotMatchSession() {
        UploadJob job = new UploadJob();
        job.setFileFingerprint("a".repeat(64));
        when(jobRepository.findByIdAndUserIdForUpdate("upload-id", "user@example.com"))
                .thenReturn(Optional.of(job));

        ResumeService service = new ResumeService(jobRepository, stateService);

        assertThrows(IllegalArgumentException.class,
                () -> service.resume("upload-id", "user@example.com", "b".repeat(64)));
        verify(stateService, never()).completedChunkIndexes("upload-id", "user@example.com");
    }
}
