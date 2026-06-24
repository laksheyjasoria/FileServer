package com.app.share.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.app.config.AppProperties;
import com.app.core.exception.InvalidShareException;
import com.app.core.exception.InvalidSharePasswordException;
import com.app.core.exception.SharePasswordRequiredException;
import com.app.share.dto.CreateShareRequest;
import com.app.share.dto.ShareResponse;
import com.app.share.entity.SharedResource;
import com.app.share.repository.SharedResourceRepository;

@Service
public class ShareService {

    private final SharedResourceRepository repo;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public ShareService(SharedResourceRepository repo,
                        PasswordEncoder encoder,
                        AppProperties props) {
        this.repo = repo;
        this.encoder = encoder;
        this.props = props;
    }

    public ShareResponse create(CreateShareRequest request,
                                String userId) {

        SharedResource share = new SharedResource();

        String token = UUID.randomUUID().toString();

        share.setToken(token);
        share.setFileId(request.getFileId());
        share.setCreatedBy(userId);
        share.setPublicAccess(request.isPublicAccess());
        share.setExpiry(request.getExpiry());

        if (request.getPassword() != null &&
                !request.getPassword().isBlank()) {

            share.setPassword(
                    encoder.encode(request.getPassword())
            );
        }

        repo.save(share);

        return new ShareResponse(
                props.getFrontendUrl() + "/share/" + token,
                token
        );
    }

    public SharedResource validate(String token,
                                   String password) {

        SharedResource share = repo.findByToken(token)
                .orElseThrow(InvalidShareException::new);

        if (share.getExpiry() != null &&
                share.getExpiry().isBefore(LocalDateTime.now())) {
            throw new InvalidShareException();
        }

        if (share.getPassword() != null) {

            if (password == null || password.isBlank()) {
                throw new SharePasswordRequiredException();
            }

            if (!encoder.matches(password, share.getPassword())) {
                throw new InvalidSharePasswordException();
            }
        }

        return share;
    }
}