package com.app.resource.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.app.drive.service.CopyService;
import com.app.drive.service.CreateFolderService;
import com.app.drive.service.DeleteService;
import com.app.drive.service.DriveService;
import com.app.drive.service.MoveService;
import com.app.drive.service.RenameService;
import com.app.resource.dto.ResourceActionRequest;
import com.app.resource.enumtype.ResourceAction;
import com.app.master.repository.MasterFileRepository;

@Service
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final DeleteService deleteService;
    private final CopyService copyService;
    private final MoveService moveService;
    private final RenameService renameService;
    private final CreateFolderService createFolderService;
    private final MasterFileRepository fileRepository;
    private final DriveService driveService;

    public ResourceService(DeleteService deleteService,
                           CopyService copyService,
                           MoveService moveService,
                           RenameService renameService,
                           CreateFolderService createFolderService,
                           MasterFileRepository fileRepository,
                           DriveService driveService) {
        this.deleteService = deleteService;
        this.copyService = copyService;
        this.moveService = moveService;
        this.renameService = renameService;
        this.createFolderService = createFolderService;
        this.fileRepository = fileRepository;
        this.driveService = driveService;
    }

    public void handle(ResourceActionRequest request, String userId) {

        if (request == null || request.getAction() == null || userId == null) {
            throw new IllegalArgumentException("A valid resource action is required");
        }
        List<String> ids = request.getIds() == null ? List.of() : request.getIds();
        if (request.getAction() != ResourceAction.CREATE_FOLDER) {
            ids.forEach(id -> requireOwned(id, userId));
        }
        if (request.getDestination() != null && !request.getDestination().isBlank()) {
            requireOwned(request.getDestination(), userId);
        }

        switch (request.getAction()) {
            case DELETE:
                ids.forEach(id -> driveService.softDelete(id, userId));
                return;

            case COPY:
                ids.forEach(id -> copyService.copy(id, request.getDestination()));
                return;

            case MOVE:
                String dest = request.getDestination();
                ids.forEach(id -> moveService.move(id, dest));
                return;

            case RENAME:
                if (ids.size() != 1) {
                    log.warn("Rename action received with {} items. Rename only supports a single item.", ids.size());
                    throw new IllegalArgumentException("Rename operation only supports one item at a time.");
                }
                String newName = request.getName();
                if (newName == null || newName.isBlank()) {
                    throw new IllegalArgumentException("New name is required for rename action.");
                }
                String id = ids.get(0);
                renameService.rename(id, newName);
                return;

            case CREATE_FOLDER:
                String name = request.getName();
                String parent = request.getDestination();
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("Folder name is required");
                }
                createFolderService.create(userId, name.trim(), parent);
                return;

            default:
                throw new IllegalArgumentException("Unsupported action: " + request.getAction());
        }
    }

    private void requireOwned(String id, String userId) {
        if (id == null || id.isBlank() || fileRepository.findByIdAndUserId(id, userId).isEmpty()) {
            throw new com.app.core.exception.FileNotFoundException();
        }
    }
}