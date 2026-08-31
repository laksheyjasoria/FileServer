package com.app.identity.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.identity.dto.FriendRequestDTO;
import com.app.identity.entity.Friend;
import com.app.identity.entity.User;
import com.app.identity.service.FriendService;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @PostMapping("/request")
    public ResponseEntity<Friend> sendRequest(@RequestParam String email, Authentication auth) {
        Friend request = friendService.sendRequest(auth.getName(), email);
        return ResponseEntity.ok(request);
    }

    @PutMapping("/accept/{requestId}")
    public ResponseEntity<FriendRequestDTO> acceptRequest(@PathVariable Long requestId, Authentication auth) {
        FriendRequestDTO request = friendService.acceptRequest(requestId, auth.getName());
        return ResponseEntity.ok(request);
    }

    @PutMapping("/reject/{requestId}")
    public ResponseEntity<Void> rejectRequest(@PathVariable Long requestId, Authentication auth) {
        friendService.rejectRequest(requestId, auth.getName());
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/request/{requestId}")
    public ResponseEntity<Void> cancelRequest(@PathVariable Long requestId, Authentication auth) {
        friendService.cancelRequest(requestId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> removeFriend(@PathVariable Long friendId, Authentication auth) {
        friendService.removeFriend(friendId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<User>> getFriends(Authentication auth) {
        return ResponseEntity.ok(friendService.getFriends(auth.getName()));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<FriendRequestDTO>> getPendingRequests(Authentication auth) {
        return ResponseEntity.ok(friendService.getPendingRequests(auth.getName()));
    }

    @GetMapping("/sent")
    public ResponseEntity<List<FriendRequestDTO>> getSentRequests(Authentication auth) {
        return ResponseEntity.ok(friendService.getSentRequests(auth.getName()));
    }
}