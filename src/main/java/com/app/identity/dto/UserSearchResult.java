package com.app.identity.dto;

public class UserSearchResult {
	private Long id;
	private String email;
	private String name;
	private String photoUrl;
	private String requestStatus;

	public UserSearchResult() {

	}

	public UserSearchResult(Long id, String email, String name, String photoUrl, String requestStatus) {
		super();
		this.id = id;
		this.email = email;
		this.name = name;
		this.photoUrl = photoUrl;
		this.requestStatus = requestStatus;
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPhotoUrl() {
		return photoUrl;
	}

	public void setPhotoUrl(String photoUrl) {
		this.photoUrl = photoUrl;
	}

	public String getRequestStatus() {
		return requestStatus;
	}

	public void setRequestStatus(String requestStatus) {
		this.requestStatus = requestStatus;
	}
}