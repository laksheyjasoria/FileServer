package com.app.master.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.master.entity.MasterFile;

public interface MasterFileRepository extends JpaRepository<MasterFile, String> {

	List<MasterFile> findByUserId(String userId);

	List<MasterFile> findByUserIdAndParentIdIsNull(String userId);

	List<MasterFile> findByUserIdAndParentId(String userId, String parentId);

	java.util.Optional<MasterFile> findByIdAndUserId(String id, String userId);

	Long countByUserId(String userId);

	Long countByParentId(String parentId);

	List<MasterFile> findByParentId(String parentId);

	List<MasterFile> findByDriveTypeAndUserIdIn(String driveType, List<String> userIds);

	Optional<MasterFile> findByIdAndDriveTypeAndUserIdIn(String id, String driveType, List<String> userIds);

	List<MasterFile> findByDriveTypeAndUserIdInAndParentIdIsNull(String driveType, List<String> userIds);
}
