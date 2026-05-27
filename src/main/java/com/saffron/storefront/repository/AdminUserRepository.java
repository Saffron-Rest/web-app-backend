package com.saffron.storefront.repository;

import com.saffron.storefront.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, String> {
    Optional<AdminUser> findByEmailIgnoreCase(String email);
    List<AdminUser> findAllByOrderByCreatedAtAsc();
    long countByActiveTrue();
}
