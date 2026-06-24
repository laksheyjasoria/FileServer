package com.app.drive.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.drive.service.DriveService;
import com.app.master.entity.MasterFile;

@RestController
@RequestMapping("/drive")
public class DriveController {

    private final DriveService service;

    public DriveController(DriveService service) {
        this.service = service;
    }

    @GetMapping
    public List<MasterFile> list(Authentication auth) {

        return service.list(auth.getName());
    }
}