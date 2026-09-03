package com.app.identity.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.identity.dto.FriendRequestDTO;
import com.app.identity.entity.Friend;
import com.app.identity.entity.User;
import com.app.identity.enums.FriendStatus;
import com.app.identity.enums.PrivacyLevel;
import com.app.identity.repository.FriendRepository;
import com.app.identity.repository.UserRepository;

@Service
public class FriendService {

	private final FriendRepository friendRepository;
	private final UserRepository userRepository;

	public FriendService(FriendRepository friendRepository, UserRepository userRepository) {
		this.friendRepository = friendRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public Friend sendRequest(String userEmail, String friendEmail) {
		User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
		User friend = userRepository.findByEmail(friendEmail)
				.orElseThrow(() -> new RuntimeException("Friend not found"));

		if (user.equals(friend)) {
			throw new RuntimeException("Cannot add yourself as friend");
		}

		// Privacy check
		PrivacyLevel privacy = friend.getFriendRequestPrivacy();
		if (privacy == PrivacyLevel.NOBODY) {
			throw new RuntimeException("This user does not accept friend requests");
		}

		// ---- Check for existing friendship or pending request ----
		Optional<Friend> existing = friendRepository.findByUserAndFriend(user, friend);
		if (existing.isPresent()) {
			FriendStatus status = existing.get().getStatus();
			if (status == FriendStatus.PENDING) {
				throw new RuntimeException("Friend request already sent to this user");
			} else if (status == FriendStatus.ACCEPTED) {
				throw new RuntimeException("You are already friends with this user");
			}
		}

		Optional<Friend> reverse = friendRepository.findByUserAndFriend(friend, user);
		if (reverse.isPresent()) {
			FriendStatus status = reverse.get().getStatus();
			if (status == FriendStatus.PENDING) {
				throw new RuntimeException("This user already sent you a friend request");
			} else if (status == FriendStatus.ACCEPTED) {
				throw new RuntimeException("You are already friends with this user");
			}
		}

		// Create request
		Friend friendRequest = new Friend();
		friendRequest.setUser(user);
		friendRequest.setFriend(friend);
		friendRequest.setStatus(friend.isAutoApproveFriends() ? FriendStatus.ACCEPTED : FriendStatus.PENDING);
		return friendRepository.save(friendRequest);
	}

	@Transactional
	public FriendRequestDTO acceptRequest(Long requestId, String currentUserEmail) {
		Friend request = friendRepository.findById(requestId)
				.orElseThrow(() -> new RuntimeException("Request not found"));
		if (!request.getFriend().getEmail().equals(currentUserEmail)) {
			throw new RuntimeException("You are not the recipient of this request");
		}
		if (request.getStatus() != FriendStatus.PENDING) {
			throw new RuntimeException("Request already handled");
		}
		request.setStatus(FriendStatus.ACCEPTED);
		Friend saved = friendRepository.save(request);
		return FriendRequestDTO.fromEntity(saved);
	}

	@Transactional
	public void rejectRequest(Long requestId, String currentUserEmail) {
		Friend request = friendRepository.findById(requestId)
				.orElseThrow(() -> new RuntimeException("Request not found"));
		if (!request.getFriend().getEmail().equals(currentUserEmail)) {
			throw new RuntimeException("You are not the recipient of this request");
		}
		friendRepository.delete(request);
	}

	@Transactional
	public void cancelRequest(Long requestId, String currentUserEmail) {
		Friend request = friendRepository.findById(requestId)
				.orElseThrow(() -> new RuntimeException("Request not found"));
		if (!request.getUser().getEmail().equals(currentUserEmail)) {
			throw new RuntimeException("You are not the sender of this request");
		}
		if (request.getStatus() != FriendStatus.PENDING) {
			throw new RuntimeException("Request already handled");
		}
		friendRepository.delete(request);
	}

	@Transactional
	public void removeFriend(Long friendId, String currentUserEmail) {
		User currentUser = userRepository.findByEmail(currentUserEmail)
				.orElseThrow(() -> new RuntimeException("User not found"));
		User friend = userRepository.findById(friendId).orElseThrow(() -> new RuntimeException("Friend not found"));

		Friend friendship = friendRepository.findByUserAndFriend(currentUser, friend).orElseGet(() -> friendRepository
				.findByUserAndFriend(friend, currentUser).orElseThrow(() -> new RuntimeException("Not friends")));
		friendRepository.delete(friendship);
	}

	@Transactional(readOnly = true)
	public List<User> getFriends(String userEmail) {
		User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
		List<User> friends = friendRepository.findAcceptedFriendsByUser(user);
		friends.addAll(friendRepository.findAcceptedByFriend(user));
		return friends.stream().distinct().collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<FriendRequestDTO> getPendingRequests(String userEmail) {
		User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
		List<Friend> pending = friendRepository.findByFriendAndStatus(user, FriendStatus.PENDING);
		return pending.stream().map(FriendRequestDTO::fromEntity).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<FriendRequestDTO> getSentRequests(String userEmail) {
		User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
		List<Friend> sent = friendRepository.findByUserAndStatus(user, FriendStatus.PENDING);
		return sent.stream().map(FriendRequestDTO::fromEntity).collect(Collectors.toList());
	}

	public boolean areFriends(User user1, User user2) {
		if (user1.equals(user2))
			return true;
		return friendRepository.findByUserAndFriend(user1, user2).map(f -> f.getStatus() == FriendStatus.ACCEPTED)
				.orElse(false)
				|| friendRepository.findByUserAndFriend(user2, user1).map(f -> f.getStatus() == FriendStatus.ACCEPTED)
						.orElse(false);
	}

	public String getFriendRequestStatus(User currentUser, User other) {
		if (areFriends(currentUser, other)) {
			return "ACCEPTED";
		}
		Optional<Friend> sent = friendRepository.findByUserAndFriend(currentUser, other);
		if (sent.isPresent() && sent.get().getStatus() == FriendStatus.PENDING) {
			return "PENDING_SENT";
		}
		Optional<Friend> received = friendRepository.findByUserAndFriend(other, currentUser);
		if (received.isPresent() && received.get().getStatus() == FriendStatus.PENDING) {
			return "PENDING_RECEIVED";
		}
		return "NONE";
	}
}