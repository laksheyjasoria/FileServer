package com.app.share.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.share.entity.SharedResource;

public interface SharedResourceRepository
        extends JpaRepository<SharedResource, String> {

    Optional<SharedResource> findByToken(String token);
}