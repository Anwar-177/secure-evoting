package com.evoting.controller;

import com.evoting.model.Election;
import com.evoting.repository.BlockRepository;
import com.evoting.repository.ElectionRepository;
import com.evoting.repository.VoterRepository;
import com.evoting.service.BlockchainService;
import com.evoting.service.ResultService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.stream.Collectors;

/** Public pages: home, published results and the blockchain audit ledger (Module 4). */
@Controller
public class HomeController {

    private final ElectionRepository electionRepository;
    private final VoterRepository voterRepository;
    private final BlockRepository blockRepository;
    private final BlockchainService blockchain;
    private final ResultService resultService;

    public HomeController(ElectionRepository electionRepository, VoterRepository voterRepository,
                          BlockRepository blockRepository, BlockchainService blockchain,
                          ResultService resultService) {
        this.electionRepository = electionRepository;
        this.voterRepository = voterRepository;
        this.blockRepository = blockRepository;
        this.blockchain = blockchain;
        this.resultService = resultService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("activeElections", electionRepository.findByStatusOrderByCreatedAtDesc(Election.Status.ACTIVE));
        model.addAttribute("voterCount", voterRepository.count());
        model.addAttribute("electionCount", electionRepository.count());
        model.addAttribute("voteCount", blockRepository.countByElectionIdNotNull());
        return "index";
    }

    @GetMapping("/results")
    public String results(Model model) {
        model.addAttribute("elections", electionRepository.findByStatusOrderByCreatedAtDesc(Election.Status.PUBLISHED));
        return "results";
    }

    @GetMapping("/results/{id}")
    public String result(@PathVariable Long id, Model model) {
        Election election = electionRepository.findById(id)
                .filter(Election::isPublished)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("election", election);
        model.addAttribute("tally", resultService.tally(election));
        model.addAttribute("chainValid", blockchain.isChainValid());
        return "result-detail";
    }

    /** Public ledger: anyone can inspect the blocks and look up a transaction hash from a vote receipt. */
    @GetMapping("/audit")
    public String audit(@RequestParam(required = false) String tx, Model model) {
        BlockchainService.ChainReport report = blockchain.validateChain();
        model.addAttribute("report", report);
        model.addAttribute("electionTitles", electionRepository.findAll().stream()
                .collect(Collectors.toMap(Election::getId, Election::getTitle)));
        if (tx != null && !tx.isBlank()) {
            String hash = tx.trim().toLowerCase(Locale.ROOT);
            model.addAttribute("tx", hash);
            model.addAttribute("found", report.checks().stream()
                    .filter(c -> c.block().getBlockHash().equals(hash)).findFirst().orElse(null));
        }
        return "audit";
    }
}
