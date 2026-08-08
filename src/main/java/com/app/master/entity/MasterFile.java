package com.app.master.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "master_files")
public class MasterFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    private String name;

    private String fileId;

    private Long size;

    private String contentType;

    // Parent folder id (null for root)
    private String parentId;

    // Type: FILE or FOLDER
    private String driveType;

    // Access type: PUBLIC or PROTECTED
    private String accessType;

    private LocalDateTime createdAt;
    
    @Transient
    private int childrenCount;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getFileId() {
        return fileId;
    }

    public Long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getParentId() {
        return parentId;
    }

    public String getDriveType() {
        return driveType;
    }

    public String getAccessType() {
        return accessType;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public void setDriveType(String driveType) {
        this.driveType = driveType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

	public int getChildrenCount() {
		return childrenCount;
	}

	public void setChildrenCount(int childrenCount) {
		this.childrenCount = childrenCount;
	}
    
}