package com.app.identity.service;

import com.app.identity.entity.User;
import com.app.identity.enums.IncomingSharePrivacy;
import com.app.identity.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PrivacyService {

    private final UserRepository userRepository;
    private final FriendService friendService;

    public PrivacyService(UserRepository userRepository, FriendService friendService) {
        this.userRepository = userRepository;
        this.friendService = friendService;
    }

    /**
     * Check if a sender is allowed to share files with the target user.
     */
    public boolean canShareWithTarget(User targetUser, User sender) {
        if (targetUser.getId().equals(sender.getId())) {
            return true; // self-sharing
        }

        IncomingSharePrivacy privacy = targetUser.getIncomingSharePrivacy();

        if (privacy == IncomingSharePrivacy.NOBODY) {
            return false;
        }
        if (privacy == IncomingSharePrivacy.FRIENDS_ONLY) {
            return friendService.areFriends(targetUser, sender);
        }
        // EVERYONE – always true
        return true;
    }

    /**
     * Filter a list of users to only those who allow the current user to share with them.
     */
    public List<User> filterAllowedRecipients(User currentUser, List<User> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .filter(target -> !target.getId().equals(currentUser.getId()))
                .filter(target -> canShareWithTarget(target, currentUser))
                .collect(Collectors.toList());
    }
}