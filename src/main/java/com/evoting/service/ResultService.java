package com.evoting.service;

import com.evoting.model.Block;
import com.evoting.model.Candidate;
import com.evoting.model.Election;
import com.evoting.repository.BlockRepository;
import com.evoting.repository.CandidateRepository;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Counts votes by reading and decrypting the vote transactions stored on the blockchain. */
@Service
public class ResultService {

    public record CandidateResult(Candidate candidate, long votes, double percent, boolean winner) { }

    /**
     * @param results      candidate-wise counts, highest first
     * @param totalVotes   valid votes counted
     * @param invalidVotes transactions that could not be decrypted / verified (tampered)
     * @param tie          true if more than one candidate has the highest count
     */
    public record Tally(List<CandidateResult> results, long totalVotes, long invalidVotes, boolean tie) {
        public List<CandidateResult> winners() {
            return results.stream().filter(CandidateResult::winner).toList();
        }
    }

    private final CandidateRepository candidateRepository;
    private final BlockRepository blockRepository;
    private final CryptoService crypto;
    private final VotingService votingService;

    public ResultService(CandidateRepository candidateRepository, BlockRepository blockRepository,
                         CryptoService crypto, VotingService votingService) {
        this.candidateRepository = candidateRepository;
        this.blockRepository = blockRepository;
        this.crypto = crypto;
        this.votingService = votingService;
    }

    public Tally tally(Election election) {
        List<Candidate> candidates = candidateRepository.findByElectionIdOrderByIdAsc(election.getId());
        Map<Long, Long> counts = new LinkedHashMap<>();
        candidates.forEach(c -> counts.put(c.getId(), 0L));

        long invalid = 0;
        for (Block block : blockRepository.findByElectionIdOrderByBlockIndexAsc(election.getId())) {
            Long candidateId = readVote(block, election.getId());
            if (candidateId != null && counts.containsKey(candidateId)) {
                counts.merge(candidateId, 1L, Long::sum);
            } else {
                invalid++;
            }
        }

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<CandidateResult> results = new ArrayList<>();
        for (Candidate c : candidates) {
            long votes = counts.get(c.getId());
            double percent = total == 0 ? 0 : Math.round(votes * 1000.0 / total) / 10.0;
            results.add(new CandidateResult(c, votes, percent, total > 0 && votes == max));
        }
        results.sort(Comparator.comparingLong(CandidateResult::votes).reversed());
        boolean tie = results.stream().filter(CandidateResult::winner).count() > 1;
        return new Tally(results, total, invalid, tie);
    }

    /** Decrypts and verifies one vote transaction. Returns the candidate id, or null if it is not valid. */
    private Long readVote(Block block, Long electionId) {
        try {
            String[] parts = crypto.decrypt(block.getEncryptedVote()).split(":");
            if (parts.length != 3 || !parts[0].equals(String.valueOf(electionId))) return null;
            Long candidateId = Long.valueOf(parts[1]);
            String expected = votingService.voteHash(electionId, candidateId, block.getVoterToken());
            return expected.equals(block.getVoteHash()) ? candidateId : null;
        } catch (GeneralSecurityException | NumberFormatException e) {
            return null;
        }
    }
}
