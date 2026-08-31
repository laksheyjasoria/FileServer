package com.app.identity.repository;

import com.app.identity.entity.Friend;
import com.app.identity.entity.User;
import com.app.identity.enums.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    Optional<Friend> findByUserAndFriend(User user, User friend);

    List<Friend> findByUserAndStatus(User user, FriendStatus status);

    List<Friend> findByFriendAndStatus(User friend, FriendStatus status);

    @Query("SELECT f.friend FROM Friend f WHERE f.user = :user AND f.status = 'ACCEPTED'")
    List<User> findAcceptedFriendsByUser(@Param("user") User user);

    @Query("SELECT f.user FROM Friend f WHERE f.friend = :user AND f.status = 'ACCEPTED'")
    List<User> findAcceptedByFriend(@Param("user") User user);
}