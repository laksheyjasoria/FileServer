package com.app.identity.service;

import com.app.identity.dto.FriendRequestDTO;
import com.app.identity.entity.Friend;
import com.app.identity.entity.User;
import com.app.identity.enums.FriendStatus;
import com.app.identity.enums.PrivacyLevel;
import com.app.identity.repository.FriendRepository;
import com.app.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User friend = userRepository.findByEmail(friendEmail)
                .orElseThrow(() -> new RuntimeException("Friend not found"));

        if (user.equals(friend)) {
            throw new RuntimeException("Cannot add yourself as friend");
        }

        PrivacyLevel privacy = friend.getFriendRequestPrivacy();
        if (privacy == PrivacyLevel.NOBODY) {
            throw new RuntimeException("This user does not accept friend requests");
        }

        if (friendRepository.findByUserAndFriend(user, friend).isPresent()) {
            throw new RuntimeException("Friend request already exists");
        }
        if (friendRepository.findByUserAndFriend(friend, user).isPresent()) {
            throw new RuntimeException("A friend request already exists from this user");
        }

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
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new RuntimeException("Friend not found"));

        Friend friendship = friendRepository.findByUserAndFriend(currentUser, friend)
                .orElseGet(() -> friendRepository.findByUserAndFriend(friend, currentUser)
                        .orElseThrow(() -> new RuntimeException("Not friends")));
        friendRepository.delete(friendship);
    }

    @Transactional(readOnly = true)
    public List<User> getFriends(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<User> friends = friendRepository.findAcceptedFriendsByUser(user);
        friends.addAll(friendRepository.findAcceptedByFriend(user));
        return friends.stream().distinct().collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendRequestDTO> getPendingRequests(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Friend> pending = friendRepository.findByFriendAndStatus(user, FriendStatus.PENDING);
        return pending.stream()
                .map(FriendRequestDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendRequestDTO> getSentRequests(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Friend> sent = friendRepository.findByUserAndStatus(user, FriendStatus.PENDING);
        return sent.stream()
                .map(FriendRequestDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public boolean areFriends(User user1, User user2) {
        if (user1.equals(user2)) return true;
        return friendRepository.findByUserAndFriend(user1, user2)
                .map(f -> f.getStatus() == FriendStatus.ACCEPTED)
                .orElse(false) ||
               friendRepository.findByUserAndFriend(user2, user1)
                .map(f -> f.getStatus() == FriendStatus.ACCEPTED)
                .orElse(false);
    }
}