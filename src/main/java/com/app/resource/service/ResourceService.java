package com.app.resource.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.app.drive.service.CopyService;
import com.app.drive.service.DeleteService;
import com.app.resource.dto.ResourceActionRequest;
import com.app.resource.enumtype.ResourceAction;

@Service
public class ResourceService {

    private final DeleteService deleteService;
    private final CopyService copyService;

    public ResourceService(DeleteService deleteService,
                           CopyService copyService) {
        this.deleteService = deleteService;
        this.copyService = copyService;
    }

    public void handle(ResourceActionRequest request) {

        List<String> ids = request.getIds();

        if (request.getAction() == ResourceAction.DELETE) {

            ids.forEach(deleteService::delete);
        }

        if (request.getAction() == ResourceAction.COPY) {

            ids.forEach(copyService::copy);
        }
    }
}