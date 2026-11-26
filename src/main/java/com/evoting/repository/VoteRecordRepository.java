package com.evoting.repository;

import com.evoting.model.VoteRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoteRecordRepository extends JpaRepository<VoteRecord, Long> {
    boolean existsByVoterIdAndElectionId(Long voterId, Long electionId);
    List<VoteRecord> findByVoterId(Long voterId);
    long countByElectionId(Long electionId);
}
