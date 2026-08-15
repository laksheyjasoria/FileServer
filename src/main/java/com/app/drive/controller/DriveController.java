package com.app.drive.controller;

import com.app.drive.service.DriveService;
import com.app.master.entity.MasterFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/drive")
public class DriveController {

    private final DriveService driveService;

    public DriveController(DriveService driveService) {
        this.driveService = driveService;
    }

    // ---------- Existing endpoints (plain objects) ----------
    @GetMapping
    public List<MasterFile> list(Authentication auth) {
        return driveService.list(auth.getName());
    }

    @GetMapping("/root")
    public List<MasterFile> root(Authentication auth) {
        return driveService.listRoot(auth.getName());
    }

    @GetMapping("/{id}/contents")
    public List<MasterFile> contents(@PathVariable String id, Authentication auth) {
        return driveService.listContents(auth.getName(), id);
    }

    @GetMapping("/{id}")
    public MasterFile get(@PathVariable String id, Authentication auth) {
        return driveService.get(auth.getName(), id);
    }

    // ---------- TRASH endpoints (plain objects) ----------
    @GetMapping("/trash")
    public List<MasterFile> listTrash(Authentication auth) {
        return driveService.listTrash(auth.getName());
    }

    @PostMapping("/trash/{id}/restore")
    public void restore(@PathVariable String id, Authentication auth) {
        driveService.restore(id, auth.getName());
    }

    @DeleteMapping("/trash/{id}")
    public void permanentDelete(@PathVariable String id, Authentication auth) {
        driveService.permanentDelete(id, auth.getName());
    }

    @DeleteMapping("/trash/empty")
    public void emptyTrash(Authentication auth) {
        driveService.emptyTrash(auth.getName());
    }
}