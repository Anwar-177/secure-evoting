package com.evoting.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Marks that a voter has voted in an election (double-vote prevention).
 * It deliberately does NOT store the chosen candidate - the choice lives only,
 * encrypted, on the blockchain.
 */
@Entity
@Table(name = "vote_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_voter_election", columnNames = {"voter_id", "election_id"}))
public class VoteRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voter_id", nullable = false)
    private Long voterId;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(nullable = false)
    private LocalDateTime votedAt = LocalDateTime.now();

    protected VoteRecord() { }

    public VoteRecord(Long voterId, Long electionId) {
        this.voterId = voterId;
        this.electionId = electionId;
    }

    public Long getId() { return id; }
    public Long getVoterId() { return voterId; }
    public Long getElectionId() { return electionId; }
    public LocalDateTime getVotedAt() { return votedAt; }
}
