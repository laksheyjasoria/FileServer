package com.app.orchestrator;

import org.springframework.stereotype.Component;

import com.app.drive.service.CopyService;
import com.app.drive.service.DeleteService;

@Component
public class DriveOrchestrator {

	private final DeleteService deleteService;
	private final CopyService copyService;

//    private final AppLogger log =
//            AppLogger.getLogger("DRIVE");

	public DriveOrchestrator(DeleteService deleteService, CopyService copyService) {
		this.deleteService = deleteService;
		this.copyService = copyService;
	}

	public void delete(String id) {

		deleteService.delete(id);

//        log.info("File deleted: {}", id);
	}

	public void copy(String id) {

		copyService.copy(id);

//        log.info("File copied: {}", id);
	}
}