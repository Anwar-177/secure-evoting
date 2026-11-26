package com.evoting.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** One block of the private blockchain. Each block carries exactly one vote transaction. */
@Entity
@Table(name = "blocks")
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private long blockIndex;

    @Column(nullable = false)
    private long createdMs;

    /** Null for the genesis block. */
    private Long electionId;

    /** Anonymous voter token: HMAC(voterId, electionId). Hides who voted, still detects duplicates. */
    @Column(length = 64)
    private String voterToken;

    /** AES-256-GCM encrypted vote. */
    @Column(length = 500)
    private String encryptedVote;

    /** HMAC-SHA256 integrity tag of the vote. */
    @Column(length = 64)
    private String voteHash;

    @Column(nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64)
    private String blockHash;

    @Column(nullable = false)
    private long nonce;

    /** Only used by the admin "simulate tampering" demo, to be able to restore the block. */
    @Column(length = 500)
    private String originalEncryptedVote;

    /** The exact string that is hashed (with SHA-256) to produce the block hash. */
    public String hashInput() {
        return blockIndex + "|" + createdMs + "|" + electionId + "|" + voterToken + "|"
                + encryptedVote + "|" + voteHash + "|" + previousHash + "|" + nonce;
    }

    public boolean isGenesis() { return blockIndex == 0; }

    public boolean isTampered() { return originalEncryptedVote != null; }

    public LocalDateTime getTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(createdMs), ZoneId.systemDefault());
    }

    public Long getId() { return id; }
    public long getBlockIndex() { return blockIndex; }
    public void setBlockIndex(long blockIndex) { this.blockIndex = blockIndex; }
    public long getCreatedMs() { return createdMs; }
    public void setCreatedMs(long createdMs) { this.createdMs = createdMs; }
    public Long getElectionId() { return electionId; }
    public void setElectionId(Long electionId) { this.electionId = electionId; }
    public String getVoterToken() { return voterToken; }
    public void setVoterToken(String voterToken) { this.voterToken = voterToken; }
    public String getEncryptedVote() { return encryptedVote; }
    public void setEncryptedVote(String encryptedVote) { this.encryptedVote = encryptedVote; }
    public String getVoteHash() { return voteHash; }
    public void setVoteHash(String voteHash) { this.voteHash = voteHash; }
    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }
    public String getBlockHash() { return blockHash; }
    public void setBlockHash(String blockHash) { this.blockHash = blockHash; }
    public long getNonce() { return nonce; }
    public void setNonce(long nonce) { this.nonce = nonce; }
    public String getOriginalEncryptedVote() { return originalEncryptedVote; }
    public void setOriginalEncryptedVote(String originalEncryptedVote) { this.originalEncryptedVote = originalEncryptedVote; }
}
