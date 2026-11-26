package com.evoting.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "voters")
public class Voter {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public voter card number, e.g. VTR2026000001. */
    @Column(unique = true, length = 20)
    private String voterCode;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(nullable = false, unique = true, length = 20)
    private String nationalId;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    /** Captured face photo as a data URL (shown to the admin during verification). */
    @Lob
    private String faceImage;

    /** Biometric template: 128 face descriptor values, comma separated. */
    @Column(length = 4000)
    private String faceDescriptor;

    /** HMAC of the registered fingerprint image (optional second factor). */
    @Column(length = 64)
    private String fingerprintHash;

    @Column(nullable = false)
    private LocalDateTime registeredAt = LocalDateTime.now();

    public boolean isApproved() { return status == Status.APPROVED; }
    public boolean isFingerprintRegistered() { return fingerprintHash != null; }

    public Long getId() { return id; }
    public String getVoterCode() { return voterCode; }
    public void setVoterCode(String voterCode) { this.voterCode = voterCode; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getFaceImage() { return faceImage; }
    public void setFaceImage(String faceImage) { this.faceImage = faceImage; }
    public String getFaceDescriptor() { return faceDescriptor; }
    public void setFaceDescriptor(String faceDescriptor) { this.faceDescriptor = faceDescriptor; }
    public String getFingerprintHash() { return fingerprintHash; }
    public void setFingerprintHash(String fingerprintHash) { this.fingerprintHash = fingerprintHash; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
}
