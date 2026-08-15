package com.app.resource.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.app.drive.service.CopyService;
import com.app.drive.service.CreateFolderService;
import com.app.drive.service.DeleteService;
import com.app.drive.service.DriveService;   // 👈 ADD this import
import com.app.drive.service.MoveService;
import com.app.drive.service.RenameService;
import com.app.resource.dto.ResourceActionRequest;
import com.app.resource.enumtype.ResourceAction;
import com.app.master.repository.MasterFileRepository;

@Service
public class ResourceService {

    private final DeleteService deleteService;
    private final CopyService copyService;
    private final MoveService moveService;
    private final RenameService renameService;
    private final CreateFolderService createFolderService;
    private final MasterFileRepository fileRepository;
    private final DriveService driveService;   // 👈 NEW field

    public ResourceService(DeleteService deleteService,
            CopyService copyService,
            MoveService moveService,
            RenameService renameService,
            CreateFolderService createFolderService,
            MasterFileRepository fileRepository,
            DriveService driveService) {        // 👈 Add to constructor
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

        if (request.getAction() == ResourceAction.DELETE) {
            // 🔁 CHANGED: now uses soft delete (move to trash)
            ids.forEach(id -> driveService.softDelete(id, userId));
            return;
        }

        if (request.getAction() == ResourceAction.COPY) {
            ids.forEach(id -> copyService.copy(id, request.getDestination()));
            return;
        }

        if (request.getAction() == ResourceAction.MOVE) {
            String dest = request.getDestination();
            ids.forEach(id -> moveService.move(id, dest));
            return;
        }

        if (request.getAction() == ResourceAction.RENAME) {
           System.out.println("can't rename all files at once");
           return;
        }

        if (request.getAction() == ResourceAction.CREATE_FOLDER) {
            String name = request.getName();
            String parent = request.getDestination();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Folder name is required");
            }
            createFolderService.create(userId, name.trim(), parent);
            return;
        }
    }

    private void requireOwned(String id, String userId) {
        if (id == null || id.isBlank() || fileRepository.findByIdAndUserId(id, userId).isEmpty()) {
            throw new com.app.core.exception.FileNotFoundException();
        }
    }
}