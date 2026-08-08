package com.app.drive.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;

@RestController
@RequestMapping("/admin/drives")
public class AdminDriveController {

    private final MasterFileRepository repo;

    public AdminDriveController(MasterFileRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<MasterFile> listAll() {
        return repo.findAll();
    }
}
