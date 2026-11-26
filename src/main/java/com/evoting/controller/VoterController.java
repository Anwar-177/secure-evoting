package com.evoting.controller;

import com.evoting.model.Block;
import com.evoting.model.Election;
import com.evoting.model.Voter;
import com.evoting.repository.CandidateRepository;
import com.evoting.repository.ElectionRepository;
import com.evoting.repository.VoterRepository;
import com.evoting.service.BiometricService;
import com.evoting.service.VotingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Module 2 (Voter Registration and Biometric Authentication) and Module 3 (Secure Voting). */
@Controller
@RequestMapping("/voter")
public class VoterController {

    private static final String VERIFIED_AT = "biometricVerifiedAt";
    private static final String FAILED_ATTEMPTS = "biometricFailedAttempts";
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** An election as shown on the voter dashboard. */
    public record ElectionView(Election election, boolean voted) { }

    private final VoterRepository voterRepository;
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final BiometricService biometric;
    private final VotingService votingService;
    private final PasswordEncoder passwordEncoder;
    private final long verificationValidMs;

    public VoterController(VoterRepository voterRepository, ElectionRepository electionRepository,
                           CandidateRepository candidateRepository, BiometricService biometric,
                           VotingService votingService, PasswordEncoder passwordEncoder,
                           @Value("${evoting.biometric.valid-minutes}") int validMinutes) {
        this.voterRepository = voterRepository;
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.biometric = biometric;
        this.votingService = votingService;
        this.passwordEncoder = passwordEncoder;
        this.verificationValidMs = validMinutes * 60_000L;
    }

    // ---------------------------------------------------------------- Registration and login

    @GetMapping("/register")
    public String registerForm() {
        return "voter/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String fullName, @RequestParam String email, @RequestParam String phone,
                           @RequestParam String nationalId, @RequestParam String dateOfBirth,
                           @RequestParam(required = false) String address,
                           @RequestParam String password, @RequestParam String confirmPassword,
                           @RequestParam(required = false) String faceImage,
                           @RequestParam(required = false) String faceDescriptor,
                           @RequestParam(required = false) MultipartFile fingerprint,
                           Model model) throws IOException {
        email = email.trim().toLowerCase(Locale.ROOT);
        nationalId = nationalId.replaceAll("\\s", "");
        phone = phone.trim();
        List<String> errors = new ArrayList<>();

        if (fullName.isBlank() || fullName.length() > 100) errors.add("Full name is required.");
        if (!email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$") || email.length() > 120) errors.add("Enter a valid email address.");
        if (!phone.matches("\\d{10}")) errors.add("Phone number must be 10 digits.");
        if (!nationalId.matches("\\d{12}")) errors.add("Aadhaar number must be 12 digits.");
        LocalDate dob = null;
        try {
            dob = LocalDate.parse(dateOfBirth);
            if (Period.between(dob, LocalDate.now()).getYears() < 18) errors.add("You must be at least 18 years old to register.");
        } catch (DateTimeParseException e) {
            errors.add("Enter a valid date of birth.");
        }
        if (password.length() < 6 || password.length() > 72) errors.add("Password must be 6 to 72 characters long.");
        if (!password.equals(confirmPassword)) errors.add("Passwords do not match.");
        double[] descriptor = biometric.parseDescriptor(faceDescriptor);
        if (descriptor == null || !biometric.isValidFaceImage(faceImage)) {
            errors.add("Face registration is required. Capture or upload a clear photo of your face.");
        }
        if (errors.isEmpty() && voterRepository.existsByEmail(email)) errors.add("This email is already registered.");
        if (errors.isEmpty() && voterRepository.existsByNationalId(nationalId)) errors.add("This Aadhaar number is already registered.");

        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("fullName", fullName);
            model.addAttribute("email", email);
            model.addAttribute("phone", phone);
            model.addAttribute("nationalId", nationalId);
            model.addAttribute("dateOfBirth", dateOfBirth);
            model.addAttribute("address", address);
            return "voter/register";
        }

        Voter voter = new Voter();
        voter.setFullName(fullName.trim());
        voter.setEmail(email);
        voter.setPhone(phone);
        voter.setNationalId(nationalId);
        voter.setDateOfBirth(dob);
        voter.setAddress(address == null || address.isBlank() ? null
                : address.trim().substring(0, Math.min(255, address.trim().length())));
        voter.setPasswordHash(passwordEncoder.encode(password));
        voter.setFaceImage(faceImage);
        voter.setFaceDescriptor(biometric.formatDescriptor(descriptor));
        if (fingerprint != null && !fingerprint.isEmpty()) {
            voter.setFingerprintHash(biometric.fingerprintTemplate(fingerprint.getBytes()));
        }
        voter = voterRepository.save(voter);
        voter.setVoterCode(String.format("VTR%d%06d", LocalDate.now().getYear(), voter.getId()));
        voterRepository.save(voter);

        model.addAttribute("voter", voter);
        return "voter/registered";
    }

    @GetMapping("/login")
    public String login() {
        return "voter/login";
    }

    // ---------------------------------------------------------------- Dashboard

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Principal principal, Model model) {
        Voter voter = currentVoter(principal);
        List<ElectionView> active = electionRepository.findByStatusOrderByCreatedAtDesc(Election.Status.ACTIVE).stream()
                .map(e -> new ElectionView(e, votingService.hasVoted(voter, e.getId()))).toList();
        List<ElectionView> past = new ArrayList<>();
        for (Election.Status status : List.of(Election.Status.CLOSED, Election.Status.PUBLISHED)) {
            electionRepository.findByStatusOrderByCreatedAtDesc(status)
                    .forEach(e -> past.add(new ElectionView(e, votingService.hasVoted(voter, e.getId()))));
        }
        model.addAttribute("voter", voter);
        model.addAttribute("active", active);
        model.addAttribute("past", past);
        return "voter/dashboard";
    }

    // ---------------------------------------------------------------- Biometric verification

    @GetMapping("/verify")
    public String verifyForm(@RequestParam Long electionId, Principal principal, Model model, RedirectAttributes flash) {
        Voter voter = currentVoter(principal);
        String blocked = votingBlockedReason(voter, electionId);
        if (blocked != null) {
            flash.addFlashAttribute("error", blocked);
            return "redirect:/voter/dashboard";
        }
        model.addAttribute("voter", voter);
        model.addAttribute("election", election(electionId));
        return "voter/verify";
    }

    @PostMapping("/verify")
    public String verify(@RequestParam Long electionId, @RequestParam(required = false) String faceDescriptor,
                         @RequestParam(required = false) MultipartFile fingerprint,
                         Principal principal, HttpSession session, RedirectAttributes flash) throws IOException {
        Voter voter = currentVoter(principal);
        String blocked = votingBlockedReason(voter, electionId);
        if (blocked != null) {
            flash.addFlashAttribute("error", blocked);
            return "redirect:/voter/dashboard";
        }

        BiometricService.FaceMatch match = biometric.matchFace(voter, biometric.parseDescriptor(faceDescriptor));
        String failure = null;
        if (!match.matched()) {
            failure = "Face verification failed. The captured face does not match your registered face.";
        } else if (fingerprint != null && !fingerprint.isEmpty()
                && !biometric.matchFingerprint(voter, fingerprint.getBytes())) {
            failure = "Fingerprint verification failed. It does not match your registered fingerprint.";
        }

        if (failure != null) {
            session.removeAttribute(VERIFIED_AT);
            Integer previous = (Integer) session.getAttribute(FAILED_ATTEMPTS);
            int attempts = previous == null ? 1 : previous + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                session.invalidate();
                return "redirect:/voter/login?locked";
            }
            session.setAttribute(FAILED_ATTEMPTS, attempts);
            flash.addFlashAttribute("error", failure + " Access denied (" + (MAX_FAILED_ATTEMPTS - attempts) + " attempts left).");
            return "redirect:/voter/verify?electionId=" + electionId;
        }

        session.removeAttribute(FAILED_ATTEMPTS);
        session.setAttribute(VERIFIED_AT, System.currentTimeMillis());
        flash.addFlashAttribute("success", "Identity verified (face match " + match.score() + "%). You may now cast your vote.");
        return "redirect:/voter/ballot/" + electionId;
    }

    // ---------------------------------------------------------------- Voting

    @GetMapping("/ballot/{electionId}")
    public String ballot(@PathVariable Long electionId, Principal principal, HttpSession session,
                         Model model, RedirectAttributes flash) {
        Voter voter = currentVoter(principal);
        String blocked = votingBlockedReason(voter, electionId);
        if (blocked != null) {
            flash.addFlashAttribute("error", blocked);
            return "redirect:/voter/dashboard";
        }
        if (!isBiometricVerified(session)) {
            return "redirect:/voter/verify?electionId=" + electionId;
        }
        model.addAttribute("election", election(electionId));
        model.addAttribute("candidates", candidateRepository.findByElectionIdOrderByIdAsc(electionId));
        return "voter/ballot";
    }

    @PostMapping("/vote")
    public String vote(@RequestParam Long electionId, @RequestParam(required = false) Long candidateId,
                       Principal principal, HttpSession session, RedirectAttributes flash) {
        Voter voter = currentVoter(principal);
        if (!isBiometricVerified(session)) {
            flash.addFlashAttribute("error", "Biometric verification is required before voting.");
            return "redirect:/voter/verify?electionId=" + electionId;
        }
        if (candidateId == null) {
            flash.addFlashAttribute("error", "Please select a candidate.");
            return "redirect:/voter/ballot/" + electionId;
        }
        try {
            votingService.castVote(voter, electionId, candidateId);
        } catch (VotingService.VotingException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/voter/dashboard";
        }
        // One verification allows exactly one vote
        session.removeAttribute(VERIFIED_AT);
        flash.addFlashAttribute("success", "Your vote has been recorded on the blockchain.");
        return "redirect:/voter/receipt/" + electionId;
    }

    @GetMapping("/receipt/{electionId}")
    public String receipt(@PathVariable Long electionId, Principal principal, Model model, RedirectAttributes flash) {
        Voter voter = currentVoter(principal);
        Block block = votingService.findReceipt(voter, electionId).orElse(null);
        if (block == null) {
            flash.addFlashAttribute("error", "You have not voted in this election.");
            return "redirect:/voter/dashboard";
        }
        model.addAttribute("voter", voter);
        model.addAttribute("election", election(electionId));
        model.addAttribute("block", block);
        return "voter/receipt";
    }

    // ---------------------------------------------------------------- Helpers

    private Voter currentVoter(Principal principal) {
        return voterRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Election election(Long id) {
        return electionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Returns why this voter cannot vote in the election right now, or null if they can. */
    private String votingBlockedReason(Voter voter, Long electionId) {
        if (!voter.isApproved()) return "Your registration must be approved by the admin before you can vote.";
        Election election = electionRepository.findById(electionId).orElse(null);
        if (election == null || !election.isActive()) return "Voting is not open for this election.";
        if (votingService.hasVoted(voter, electionId)) return "You have already voted in this election.";
        return null;
    }

    private boolean isBiometricVerified(HttpSession session) {
        Long verifiedAt = (Long) session.getAttribute(VERIFIED_AT);
        return verifiedAt != null && System.currentTimeMillis() - verifiedAt <= verificationValidMs;
    }
}
