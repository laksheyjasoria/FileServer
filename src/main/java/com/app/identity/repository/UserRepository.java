package com.app.identity.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.app.identity.entity.User;
import com.app.identity.enums.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByEmail(String email);

	List<User> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(String email, String name);

	Page<User> findAllByStatusNot(UserStatus status, Pageable pageable);

	Page<User> findAllByStatusAndEmailContainingIgnoreCase(UserStatus status, String email, Pageable pageable);

	long countByStatus(UserStatus status);

	Page<User> findAllByStatus(UserStatus status, Pageable pageable);

	@Query("SELECT u FROM User u WHERE u.status = :status AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')))")
	Page<User> findAllByStatusAndEmailOrNameContainingIgnoreCase(@Param("status") UserStatus status,
			@Param("search") String search, Pageable pageable);

	@Query("SELECT u FROM User u WHERE u.status != :excludedStatus AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')))")
	Page<User> findAllByEmailOrNameContainingIgnoreCaseAndStatusNot(@Param("search") String search,
			@Param("excludedStatus") UserStatus excludedStatus, Pageable pageable);
	
	long countByIdIn(java.util.Collection<Long> ids);
}
