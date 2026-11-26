package com.evoting.service;

import com.evoting.model.Block;
import com.evoting.repository.BlockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Private Java blockchain: block creation (proof of work) and chain validation. */
@Service
public class BlockchainService {

    public static final String GENESIS_PREVIOUS_HASH = "0".repeat(64);

    /** Validation outcome of a single block. */
    public record BlockCheck(Block block, boolean hashValid, boolean linkValid, boolean workValid) {
        public boolean valid() { return hashValid && linkValid && workValid; }

        public String problem() {
            if (!hashValid) return "Block data was modified (hash mismatch)";
            if (!linkValid) return "Broken link to previous block";
            if (!workValid) return "Proof of work is invalid";
            return "";
        }
    }

    /** Validation outcome of the whole chain. */
    public record ChainReport(List<BlockCheck> checks, boolean valid, long invalidCount) { }

    private final BlockRepository blockRepository;
    private final CryptoService crypto;
    private final String workPrefix;

    public BlockchainService(BlockRepository blockRepository, CryptoService crypto,
                             @Value("${evoting.blockchain.difficulty}") int difficulty) {
        this.blockRepository = blockRepository;
        this.crypto = crypto;
        this.workPrefix = "0".repeat(Math.max(1, Math.min(difficulty, 5)));
    }

    public int getDifficulty() {
        return workPrefix.length();
    }

    /** Creates the genesis block if the chain is empty. */
    public synchronized void ensureGenesisBlock() {
        if (blockRepository.count() > 0) return;
        Block genesis = new Block();
        genesis.setBlockIndex(0);
        genesis.setCreatedMs(System.currentTimeMillis());
        genesis.setPreviousHash(GENESIS_PREVIOUS_HASH);
        mine(genesis);
        blockRepository.save(genesis);
    }

    /** Appends a vote transaction as a new mined block. Callers must serialize access. */
    public Block addVoteBlock(Long electionId, String voterToken, String encryptedVote, String voteHash) {
        Block last = blockRepository.findTopByOrderByBlockIndexDesc()
                .orElseThrow(() -> new IllegalStateException("Blockchain has no genesis block"));
        Block block = new Block();
        block.setBlockIndex(last.getBlockIndex() + 1);
        block.setCreatedMs(System.currentTimeMillis());
        block.setElectionId(electionId);
        block.setVoterToken(voterToken);
        block.setEncryptedVote(encryptedVote);
        block.setVoteHash(voteHash);
        block.setPreviousHash(last.getBlockHash());
        mine(block);
        return blockRepository.save(block);
    }

    public String calculateHash(Block block) {
        return crypto.sha256(block.hashInput());
    }

    /** Re-computes every hash and checks every link of the chain. */
    public ChainReport validateChain() {
        List<Block> chain = blockRepository.findAllByOrderByBlockIndexAsc();
        List<BlockCheck> checks = new ArrayList<>();
        long invalid = 0;
        Block previous = null;
        for (Block block : chain) {
            boolean hashValid = calculateHash(block).equals(block.getBlockHash());
            boolean linkValid = previous == null
                    ? block.getBlockIndex() == 0 && GENESIS_PREVIOUS_HASH.equals(block.getPreviousHash())
                    : block.getBlockIndex() == previous.getBlockIndex() + 1
                      && block.getPreviousHash().equals(previous.getBlockHash());
            boolean workValid = block.getBlockHash().startsWith(workPrefix);
            BlockCheck check = new BlockCheck(block, hashValid, linkValid, workValid);
            if (!check.valid()) invalid++;
            checks.add(check);
            previous = block;
        }
        return new ChainReport(checks, invalid == 0 && !chain.isEmpty(), invalid);
    }

    public boolean isChainValid() {
        return validateChain().valid();
    }

    private void mine(Block block) {
        long nonce = 0;
        String hash;
        do {
            block.setNonce(nonce++);
            hash = calculateHash(block);
        } while (!hash.startsWith(workPrefix));
        block.setBlockHash(hash);
    }
}
