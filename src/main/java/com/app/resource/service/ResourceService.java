package com.app.resource.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.app.drive.service.CopyService;
import com.app.drive.service.CreateFolderService;
import com.app.drive.service.DeleteService;
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

    public ResourceService(DeleteService deleteService,
            CopyService copyService,
            MoveService moveService,
            RenameService renameService,
            CreateFolderService createFolderService,
            MasterFileRepository fileRepository) {
        this.deleteService = deleteService;
        this.copyService = copyService;
        this.moveService = moveService;
        this.renameService = renameService;
        this.createFolderService = createFolderService;
        this.fileRepository = fileRepository;
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
            ids.forEach(deleteService::delete);
            return;
        }

        if (request.getAction() == ResourceAction.COPY) {
            ids.forEach(id -> copyService.copy(id, request.getDestination()));
            return;
        }

        if (request.getAction() == ResourceAction.MOVE) {
            // destination contains destination folder id
            String dest = request.getDestination();
            ids.forEach(id -> moveService.move(id, dest));
            return;
        }

        if (request.getAction() == ResourceAction.RENAME) {
            // name contains new name
            String newName = request.getName();
            ids.forEach(id -> renameService.rename(id, newName));
            return;
        }

        if (request.getAction() == ResourceAction.CREATE_FOLDER) {
            // name contains folder name, destination contains parentId (optional)
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
