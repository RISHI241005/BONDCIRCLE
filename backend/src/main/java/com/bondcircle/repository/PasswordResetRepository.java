package com.bondcircle.repository;

import com.bondcircle.entity.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {
    Optional<PasswordReset> findByEmail(String email);
    Optional<PasswordReset> findByResetToken(String resetToken);
    void deleteByEmail(String email);
}
