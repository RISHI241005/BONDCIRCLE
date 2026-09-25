package com.bondcircle.repository;

import com.bondcircle.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    Optional<UserProfile> findByUserEmail(String email);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
