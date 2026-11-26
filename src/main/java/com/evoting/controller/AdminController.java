package com.evoting.controller;

import com.evoting.model.Block;
import com.evoting.model.Candidate;
import com.evoting.model.Election;
import com.evoting.model.Voter;
import com.evoting.repository.BlockRepository;
import com.evoting.repository.CandidateRepository;
import com.evoting.repository.ElectionRepository;
import com.evoting.repository.VoteRecordRepository;
import com.evoting.repository.VoterRepository;
import com.evoting.service.BlockchainService;
import com.evoting.service.ResultService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Module 1 (Admin / Election Management) and the admin side of Module 4 (verification and results). */
@Controller
@RequestMapping("/admin")
public class AdminController {

    /** One line of the dashboard / election list. */
    public record ElectionRow(Election election, long candidates, long votes, int turnout) { }

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final VoterRepository voterRepository;
    private final VoteRecordRepository voteRecordRepository;
    private final BlockRepository blockRepository;
    private final BlockchainService blockchain;
    private final ResultService resultService;

    public AdminController(ElectionRepository electionRepository, CandidateRepository candidateRepository,
                           VoterRepository voterRepository, VoteRecordRepository voteRecordRepository,
                           BlockRepository blockRepository, BlockchainService blockchain,
                           ResultService resultService) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.voterRepository = voterRepository;
        this.voteRecordRepository = voteRecordRepository;
        this.blockRepository = blockRepository;
        this.blockchain = blockchain;
        this.resultService = resultService;
    }

    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    // ---------------------------------------------------------------- Dashboard

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("totalVoters", voterRepository.count());
        model.addAttribute("pendingVoters", voterRepository.countByStatus(Voter.Status.PENDING));
        model.addAttribute("approvedVoters", voterRepository.countByStatus(Voter.Status.APPROVED));
        model.addAttribute("totalElections", electionRepository.count());
        model.addAttribute("activeElections", electionRepository.countByStatus(Election.Status.ACTIVE));
        model.addAttribute("totalVotes", blockRepository.countByElectionIdNotNull());
        model.addAttribute("totalBlocks", blockRepository.count());
        model.addAttribute("chainValid", blockchain.isChainValid());
        model.addAttribute("rows", electionRows());
        return "admin/dashboard";
    }

    // ---------------------------------------------------------------- Elections

    @GetMapping("/elections")
    public String elections(Model model) {
        model.addAttribute("rows", electionRows());
        return "admin/elections";
    }

    @PostMapping("/elections")
    public String createElection(@RequestParam String title, @RequestParam(required = false) String description,
                                 RedirectAttributes flash) {
        if (title.isBlank() || title.length() > 150) {
            flash.addFlashAttribute("error", "Election title is required (max 150 characters).");
            return "redirect:/admin/elections";
        }
        Election election = new Election();
        election.setTitle(title.trim());
        election.setDescription(limit(description, 1000));
        electionRepository.save(election);
        flash.addFlashAttribute("success", "Election created. Now add the candidates.");
        return "redirect:/admin/elections/" + election.getId();
    }

    @GetMapping("/elections/{id}")
    public String electionDetail(@PathVariable Long id, Model model) {
        Election election = election(id);
        long votes = voteRecordRepository.countByElectionId(id);
        long approved = voterRepository.countByStatus(Voter.Status.APPROVED);
        model.addAttribute("election", election);
        model.addAttribute("candidates", candidateRepository.findByElectionIdOrderByIdAsc(id));
        model.addAttribute("votes", votes);
        model.addAttribute("approvedVoters", approved);
        model.addAttribute("turnout", percent(votes, approved));
        // Votes are counted only after voting has stopped, so nobody sees a running tally
        if (election.isClosed() || election.isPublished()) {
            model.addAttribute("tally", resultService.tally(election));
            model.addAttribute("chainValid", blockchain.isChainValid());
        }
        return "admin/election-detail";
    }

    @PostMapping("/elections/{id}/candidates")
    public String addCandidate(@PathVariable Long id, @RequestParam String name, @RequestParam String party,
                               @RequestParam(required = false) String manifesto, RedirectAttributes flash) {
        Election election = election(id);
        if (!election.isDraft()) {
            flash.addFlashAttribute("error", "Candidates can only be changed before the election starts.");
        } else if (name.isBlank() || party.isBlank() || name.length() > 100 || party.length() > 100) {
            flash.addFlashAttribute("error", "Candidate name and party are required (max 100 characters each).");
        } else {
            Candidate candidate = new Candidate();
            candidate.setElection(election);
            candidate.setName(name.trim());
            candidate.setParty(party.trim());
            candidate.setManifesto(limit(manifesto, 500));
            candidateRepository.save(candidate);
            flash.addFlashAttribute("success", "Candidate added.");
        }
        return "redirect:/admin/elections/" + id;
    }

    @PostMapping("/candidates/{id}/delete")
    public String deleteCandidate(@PathVariable Long id, RedirectAttributes flash) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Election election = candidate.getElection();
        if (election.isDraft()) {
            candidateRepository.delete(candidate);
            flash.addFlashAttribute("success", "Candidate removed.");
        } else {
            flash.addFlashAttribute("error", "Candidates can only be changed before the election starts.");
        }
        return "redirect:/admin/elections/" + election.getId();
    }

    @PostMapping("/elections/{id}/start")
    public String startElection(@PathVariable Long id, RedirectAttributes flash) {
        Election election = election(id);
        if (!election.isDraft()) {
            flash.addFlashAttribute("error", "This election has already been started.");
        } else if (candidateRepository.countByElectionId(id) < 2) {
            flash.addFlashAttribute("error", "Add at least two candidates before starting the election.");
        } else {
            election.setStatus(Election.Status.ACTIVE);
            election.setStartedAt(LocalDateTime.now());
            electionRepository.save(election);
            flash.addFlashAttribute("success", "Election started. Voting is now open.");
        }
        return "redirect:/admin/elections/" + id;
    }

    @PostMapping("/elections/{id}/stop")
    public String stopElection(@PathVariable Long id, RedirectAttributes flash) {
        Election election = election(id);
        if (!election.isActive()) {
            flash.addFlashAttribute("error", "Only an active election can be stopped.");
        } else {
            election.setStatus(Election.Status.CLOSED);
            election.setEndedAt(LocalDateTime.now());
            electionRepository.save(election);
            flash.addFlashAttribute("success", "Election stopped. Verify the blockchain and publish the result.");
        }
        return "redirect:/admin/elections/" + id;
    }

    @PostMapping("/elections/{id}/publish")
    public String publishResult(@PathVariable Long id, RedirectAttributes flash) {
        Election election = election(id);
        if (!election.isClosed()) {
            flash.addFlashAttribute("error", "Stop the election before publishing its result.");
        } else if (!blockchain.isChainValid()) {
            flash.addFlashAttribute("error",
                    "Blockchain validation failed - tampering detected. The result cannot be published.");
        } else if (resultService.tally(election).invalidVotes() > 0) {
            flash.addFlashAttribute("error", "Some vote transactions failed verification. The result cannot be published.");
        } else {
            election.setStatus(Election.Status.PUBLISHED);
            election.setPublishedAt(LocalDateTime.now());
            electionRepository.save(election);
            flash.addFlashAttribute("success", "Blockchain verified. The result is now public.");
        }
        return "redirect:/admin/elections/" + id;
    }

    @PostMapping("/elections/{id}/delete")
    public String deleteElection(@PathVariable Long id, RedirectAttributes flash) {
        Election election = election(id);
        if (!election.isDraft()) {
            flash.addFlashAttribute("error", "Only a draft election can be deleted.");
            return "redirect:/admin/elections/" + id;
        }
        candidateRepository.deleteAll(candidateRepository.findByElectionIdOrderByIdAsc(id));
        electionRepository.delete(election);
        flash.addFlashAttribute("success", "Draft election deleted.");
        return "redirect:/admin/elections";
    }

    // ---------------------------------------------------------------- Voters

    @GetMapping("/voters")
    public String voters(Model model) {
        model.addAttribute("voters", voterRepository.findAllByOrderByRegisteredAtDesc());
        model.addAttribute("pendingCount", voterRepository.countByStatus(Voter.Status.PENDING));
        return "admin/voters";
    }

    @PostMapping("/voters/{id}/approve")
    public String approveVoter(@PathVariable Long id, RedirectAttributes flash) {
        return setVoterStatus(id, Voter.Status.APPROVED, flash);
    }

    @PostMapping("/voters/{id}/reject")
    public String rejectVoter(@PathVariable Long id, RedirectAttributes flash) {
        return setVoterStatus(id, Voter.Status.REJECTED, flash);
    }

    private String setVoterStatus(Long id, Voter.Status status, RedirectAttributes flash) {
        Voter voter = voterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        voter.setStatus(status);
        voterRepository.save(voter);
        flash.addFlashAttribute("success", voter.getFullName() + " is now " + status.name().toLowerCase() + ".");
        return "redirect:/admin/voters";
    }

    // ---------------------------------------------------------------- Blockchain

    @GetMapping("/blockchain")
    public String blockchain(Model model) {
        model.addAttribute("report", blockchain.validateChain());
        model.addAttribute("difficulty", blockchain.getDifficulty());
        model.addAttribute("electionTitles", electionTitles());
        return "admin/blockchain";
    }

    /** Demo only: changes the stored vote of a block directly in the database, like an attacker would. */
    @PostMapping("/blockchain/{id}/tamper")
    public String tamperBlock(@PathVariable Long id, RedirectAttributes flash) {
        Block block = blockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (block.isGenesis() || block.isTampered()) {
            flash.addFlashAttribute("error", "This block cannot be used for the tampering demo.");
        } else {
            block.setOriginalEncryptedVote(block.getEncryptedVote());
            block.setEncryptedVote(new StringBuilder(block.getEncryptedVote()).reverse().toString());
            blockRepository.save(block);
            flash.addFlashAttribute("error", "Block #" + block.getBlockIndex()
                    + " was modified in the database. Validation now detects the tampering.");
        }
        return "redirect:/admin/blockchain";
    }

    @PostMapping("/blockchain/{id}/restore")
    public String restoreBlock(@PathVariable Long id, RedirectAttributes flash) {
        Block block = blockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (block.isTampered()) {
            block.setEncryptedVote(block.getOriginalEncryptedVote());
            block.setOriginalEncryptedVote(null);
            blockRepository.save(block);
            flash.addFlashAttribute("success", "Block #" + block.getBlockIndex() + " restored. The chain is valid again.");
        }
        return "redirect:/admin/blockchain";
    }

    // ---------------------------------------------------------------- Helpers

    private Election election(Long id) {
        return electionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private List<ElectionRow> electionRows() {
        long approved = voterRepository.countByStatus(Voter.Status.APPROVED);
        return electionRepository.findAllByOrderByCreatedAtDesc().stream().map(e -> {
            long votes = voteRecordRepository.countByElectionId(e.getId());
            return new ElectionRow(e, candidateRepository.countByElectionId(e.getId()), votes, percent(votes, approved));
        }).toList();
    }

    private Map<Long, String> electionTitles() {
        return electionRepository.findAll().stream().collect(Collectors.toMap(Election::getId, Election::getTitle));
    }

    private static int percent(long part, long whole) {
        return whole == 0 ? 0 : (int) Math.min(100, Math.round(part * 100.0 / whole));
    }

    private static String limit(String text, int max) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
