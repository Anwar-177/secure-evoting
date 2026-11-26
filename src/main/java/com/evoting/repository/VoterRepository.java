package com.evoting.repository;

import com.evoting.model.Voter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoterRepository extends JpaRepository<Voter, Long> {
    Optional<Voter> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNationalId(String nationalId);
    long countByStatus(Voter.Status status);
    List<Voter> findAllByOrderByRegisteredAtDesc();
}
