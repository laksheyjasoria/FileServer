package com.app.identity.dto;

import java.time.Instant;

import com.app.identity.entity.Friend;

public class FriendResponseDTO {
	private Long id;
	private Long userId;
	private String userEmail;
	private String userName;
	private Long friendId;
	private String friendEmail;
	private String friendName;
	private String status;
	private Instant createdAt;

	public static FriendResponseDTO fromEntity(Friend friend) {
		FriendResponseDTO dto = new FriendResponseDTO();
		dto.setId(friend.getId());
		dto.setUserId(friend.getUser().getId());
		dto.setUserEmail(friend.getUser().getEmail());
		dto.setUserName(friend.getUser().getName());
		dto.setFriendId(friend.getFriend().getId());
		dto.setFriendEmail(friend.getFriend().getEmail());
		dto.setFriendName(friend.getFriend().getName());
		dto.setStatus(friend.getStatus().name());
		dto.setCreatedAt(friend.getCreatedAt());
		return dto;
	}

	// Getters and setters...
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getUserEmail() {
		return userEmail;
	}

	public void setUserEmail(String userEmail) {
		this.userEmail = userEmail;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public Long getFriendId() {
		return friendId;
	}

	public void setFriendId(Long friendId) {
		this.friendId = friendId;
	}

	public String getFriendEmail() {
		return friendEmail;
	}

	public void setFriendEmail(String friendEmail) {
		this.friendEmail = friendEmail;
	}

	public String getFriendName() {
		return friendName;
	}

	public void setFriendName(String friendName) {
		this.friendName = friendName;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}