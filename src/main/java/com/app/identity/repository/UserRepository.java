package com.app.identity.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.app.identity.entity.User;
import com.app.identity.entity.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    List<User> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(String email, String name);

    Page<User> findAllByStatusNot(UserStatus status, Pageable pageable);

    Page<User> findAllByStatusAndEmailContainingIgnoreCase(UserStatus status, String email, Pageable pageable);

    long countByStatus(UserStatus status);

    Page<User> findAllByStatus(UserStatus status, Pageable pageable);
}