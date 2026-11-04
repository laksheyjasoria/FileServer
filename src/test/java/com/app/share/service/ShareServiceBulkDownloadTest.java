package com.app.share.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.app.config.AppProperties;
import com.app.core.exception.ShareAccessDeniedException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.share.entity.SharePermission;
import com.app.share.entity.SharedResource;
import com.app.share.repository.SharedResourceRepository;

@ExtendWith(MockitoExtension.class)
class ShareServiceBulkDownloadTest {

    @Mock
    private SharedResourceRepository shareRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AppProperties appProperties;

    @Mock
    private MasterFileRepository fileRepository;

    private ShareService shareService;

    @BeforeEach
    void setUp() {
        shareService = new ShareService(shareRepository, passwordEncoder, appProperties, fileRepository);
    }

    @Test
    void allowsBulkDownloadOnlyForAnAuthorizedRootAndDownloadPermission() {
        SharedResource share = publicShare("root-file");
        share.setPermission(SharePermission.VIEW_DOWNLOAD);
        MasterFile file = new MasterFile();

        when(shareRepository.findByToken("share-token")).thenReturn(Optional.of(share));
        when(fileRepository.findByIdAndUserIdAndActiveTrue("root-file", "owner@example.com"))
                .thenReturn(Optional.of(file));

        assertSame(share, shareService.validateBulkDownload("share-token", null, List.of("root-file")));
    }

    @Test
    void rejectsBulkDownloadForViewOnlyShare() {
        SharedResource share = publicShare("root-file");
        share.setPermission(SharePermission.VIEW);

        when(shareRepository.findByToken("share-token")).thenReturn(Optional.of(share));

        assertThrows(ShareAccessDeniedException.class,
                () -> shareService.validateBulkDownload("share-token", null, List.of("root-file")));
    }

    @Test
    void rejectsItemsOutsideTheSharedRoot() {
        SharedResource share = publicShare("root-file");
        share.setPermission(SharePermission.DOWNLOAD);

        when(shareRepository.findByToken("share-token")).thenReturn(Optional.of(share));

        assertThrows(ShareAccessDeniedException.class,
                () -> shareService.validateBulkDownload("share-token", null, List.of("other-user-file")));
    }

    private SharedResource publicShare(String rootId) {
        SharedResource share = new SharedResource();
        share.setToken("share-token");
        share.setFileId(rootId);
        share.setCreatedBy("owner@example.com");
        share.setPublicAccess(true);
        share.setPermission(SharePermission.VIEW_DOWNLOAD);
        return share;
    }
}
