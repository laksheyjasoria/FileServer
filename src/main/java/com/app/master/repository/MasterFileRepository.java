package com.app.master.repository;

import com.app.master.entity.MasterFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface MasterFileRepository extends JpaRepository<MasterFile, String> {

    // ---------- Existing methods ----------
    List<MasterFile> findByUserId(String userId);

    List<MasterFile> findByUserIdAndParentIdIsNull(String userId);

    List<MasterFile> findByUserIdAndParentId(String userId, String parentId);

    Optional<MasterFile> findByIdAndUserId(String id, String userId);

    Long countByUserId(String userId);

    Long countByParentId(String parentId);

    List<MasterFile> findByParentId(String parentId);

    List<MasterFile> findByDriveTypeAndUserIdIn(String driveType, List<String> userIds);

    Optional<MasterFile> findByIdAndDriveTypeAndUserIdIn(String id, String driveType, List<String> userIds);

    List<MasterFile> findByDriveTypeAndUserIdInAndParentIdIsNull(String driveType, List<String> userIds);

    // ---------- New active‑filtered methods ----------
    List<MasterFile> findByUserIdAndParentIdIsNullAndActiveTrue(String userId);

    Long countByParentIdAndActiveTrue(String parentId);

    List<MasterFile> findByUserIdAndParentIdAndActiveTrue(String userId, String parentId);

    Optional<MasterFile> findByIdAndUserIdAndActiveTrue(String id, String userId);

    Optional<MasterFile> findByIdAndUserIdAndActiveFalse(String id, String userId);

    List<MasterFile> findByUserIdAndActiveFalseAndDeletedAtIsNotNull(String userId);

    List<MasterFile> findByParentIdAndActiveTrue(String parentId);

    List<MasterFile> findByParentIdAndActiveFalse(String parentId);

    // ---------- Recursive CTE (table name fixed to master_files) ----------
    @Query(value = "WITH RECURSIVE descendants AS ( " +
                   "    SELECT id FROM master_files WHERE id = :folderId " +
                   "    UNION ALL " +
                   "    SELECT mf.id FROM master_files mf " +
                   "    INNER JOIN descendants d ON d.id = mf.parent_id " +
                   ") SELECT id FROM descendants WHERE id != :folderId", nativeQuery = true)
    List<String> findAllDescendantIds(@Param("folderId") String folderId);

    // ---------- Bulk soft-delete descendants ----------
    @Modifying
    @Transactional
    @Query("UPDATE MasterFile f SET f.active = false, f.deletedAt = CURRENT_TIMESTAMP WHERE f.id IN :ids")
    void softDeleteAllByIds(@Param("ids") List<String> ids);

    // ---------- Bulk restore descendants ----------
    @Modifying
    @Transactional
    @Query("UPDATE MasterFile f SET f.active = true, f.deletedAt = null WHERE f.id IN :ids")
    void restoreAllByIds(@Param("ids") List<String> ids);
}