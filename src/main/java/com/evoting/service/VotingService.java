package com.evoting.service;

import com.evoting.model.Block;
import com.evoting.model.Candidate;
import com.evoting.model.Election;
import com.evoting.model.VoteRecord;
import com.evoting.model.Voter;
import com.evoting.repository.BlockRepository;
import com.evoting.repository.CandidateRepository;
import com.evoting.repository.ElectionRepository;
import com.evoting.repository.VoteRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/** Secure vote casting: eligibility checks, double-vote prevention, encryption, blockchain transaction. */
@Service
public class VotingService {

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final VoteRecordRepository voteRecordRepository;
    private final BlockRepository blockRepository;
    private final BlockchainService blockchain;
    private final CryptoService crypto;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();

    public VotingService(ElectionRepository electionRepository, CandidateRepository candidateRepository,
                         VoteRecordRepository voteRecordRepository, BlockRepository blockRepository,
                         BlockchainService blockchain, CryptoService crypto,
                         PlatformTransactionManager transactionManager) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.voteRecordRepository = voteRecordRepository;
        this.blockRepository = blockRepository;
        this.blockchain = blockchain;
        this.crypto = crypto;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Anonymous, per-election token of a voter. The same voter always maps to the same token. */
    public String voterToken(Long voterId, Long electionId) {
        return crypto.hmac("voter|" + voterId + "|" + electionId);
    }

    /** Integrity tag binding a vote to its election, candidate and voter token. */
    public String voteHash(Long electionId, Long candidateId, String voterToken) {
        return crypto.hmac("vote|" + electionId + "|" + candidateId + "|" + voterToken);
    }

    public boolean hasVoted(Voter voter, Long electionId) {
        return voteRecordRepository.existsByVoterIdAndElectionId(voter.getId(), electionId);
    }

    public Optional<Block> findReceipt(Voter voter, Long electionId) {
        return blockRepository.findByElectionIdAndVoterToken(electionId, voterToken(voter.getId(), electionId));
    }

    /**
     * Casts one vote. Synchronized so that blocks are appended one at a time and the
     * "already voted" check and the block creation happen atomically.
     *
     * @throws VotingException if the voter is not allowed to vote
     */
    public synchronized Block castVote(Voter voter, Long electionId, Long candidateId) {
        return transaction.execute(tx -> {
            if (!voter.isApproved()) {
                throw new VotingException("Your voter registration has not been approved.");
            }
            Election election = electionRepository.findById(electionId)
                    .orElseThrow(() -> new VotingException("Election not found."));
            if (!election.isActive()) {
                throw new VotingException("Voting is not open for this election.");
            }
            Candidate candidate = candidateRepository.findById(candidateId)
                    .filter(c -> c.getElection().getId().equals(electionId))
                    .orElseThrow(() -> new VotingException("Please select a valid candidate."));

            String token = voterToken(voter.getId(), electionId);
            if (voteRecordRepository.existsByVoterIdAndElectionId(voter.getId(), electionId)
                    || blockRepository.existsByElectionIdAndVoterToken(electionId, token)) {
                throw new VotingException("You have already voted in this election.");
            }

            // Random salt so that two votes for the same candidate never look alike
            byte[] salt = new byte[8];
            random.nextBytes(salt);
            String encryptedVote = crypto.encrypt(
                    electionId + ":" + candidate.getId() + ":" + HexFormat.of().formatHex(salt));
            String voteHash = voteHash(electionId, candidate.getId(), token);

            voteRecordRepository.save(new VoteRecord(voter.getId(), electionId));
            return blockchain.addVoteBlock(electionId, token, encryptedVote, voteHash);
        });
    }

    public static class VotingException extends RuntimeException {
        public VotingException(String message) {
            super(message);
        }
    }
}
