package com.evoting.repository;

import com.evoting.model.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {
    List<Block> findAllByOrderByBlockIndexAsc();
    List<Block> findByElectionIdOrderByBlockIndexAsc(Long electionId);
    Optional<Block> findTopByOrderByBlockIndexDesc();
    Optional<Block> findByBlockHash(String blockHash);
    Optional<Block> findByElectionIdAndVoterToken(Long electionId, String voterToken);
    boolean existsByElectionIdAndVoterToken(Long electionId, String voterToken);
    long countByElectionIdNotNull();
}
