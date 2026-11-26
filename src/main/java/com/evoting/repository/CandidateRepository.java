package com.evoting.repository;

import com.evoting.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findByElectionIdOrderByIdAsc(Long electionId);
    long countByElectionId(Long electionId);
}
