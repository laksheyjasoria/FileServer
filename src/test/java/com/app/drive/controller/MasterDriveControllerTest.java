package com.app.drive.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.app.core.exception.FileNotFoundException;
import com.app.drive.service.MasterDriveAsyncUploadService;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.orchestrator.UploadOrchestrator;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRangeParser;
import com.app.upload.service.CancelService;
import com.app.upload.service.ChunkService;
import com.app.upload.service.ChunkUploadStateService;
import com.app.upload.service.PauseService;
import com.app.upload.service.ResumeService;
import com.app.upload.service.UploadStatusService;

class MasterDriveControllerTest {

    private final MasterFileRepository files = mock(MasterFileRepository.class);
    private final MasterDriveController controller = new MasterDriveController(
            files,
            mock(UploadOrchestrator.class),
            mock(ChunkService.class),
            mock(ChunkUploadStateService.class),
            mock(MasterDriveAsyncUploadService.class),
            mock(ResumeService.class),
            mock(PauseService.class),
            mock(CancelService.class),
            mock(UploadStatusService.class),
            mock(FileTransferService.class),
            mock(HttpRangeParser.class),
            mock(com.app.core.security.jwt.JwtService.class));

    @Test
    void resolvesTheConfiguredRootDriveNameWithoutCaseSensitivity() {
        MasterFile drive = rootDrive("DigitalInvitation", "admin", true);
        when(files.findByDriveTypeAndUserIdInAndParentIdIsNull(eq("FOLDER"), anyList()))
                .thenReturn(List.of(drive));

        assertSame(drive, controller.getDriveByName("digitalinvitation"));
    }

    @Test
    void doesNotResolveAnInactiveRootDrive() {
        MasterFile drive = rootDrive("DigitalInvitation", "admin", false);
        when(files.findByDriveTypeAndUserIdInAndParentIdIsNull(eq("FOLDER"), anyList()))
                .thenReturn(List.of(drive));

        assertThrows(FileNotFoundException.class,
                () -> controller.getDriveByName("DigitalInvitation"));
    }

    @Test
    void rejectsARegularUsersRootAsAMasterDrive() {
        MasterFile drive = rootDrive("PrivateDrive", "user-123", true);
        drive.setId("private-root");
        when(files.findById("private-root")).thenReturn(Optional.of(drive));

        assertThrows(FileNotFoundException.class, () -> controller.getDrive("private-root"));
    }

    @Test
    void rejectsADeletedDriveById() {
        MasterFile drive = rootDrive("DigitalInvitation", "admin", false);
        drive.setId("deleted-root");
        when(files.findById("deleted-root")).thenReturn(Optional.of(drive));

        assertThrows(FileNotFoundException.class, () -> controller.getDrive("deleted-root"));
    }

    private MasterFile rootDrive(String name, String owner, boolean active) {
        MasterFile drive = new MasterFile();
        drive.setName(name);
        drive.setUserId(owner);
        drive.setDriveType("FOLDER");
        drive.setParentId(null);
        drive.setActive(active);
        return drive;
    }
}
