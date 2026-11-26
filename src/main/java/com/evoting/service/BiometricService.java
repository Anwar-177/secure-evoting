package com.evoting.service;

import com.evoting.model.Voter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Biometric template handling.
 * <p>
 * Face: the browser extracts a 128-value face descriptor (face-api.js neural network); the server stores it
 * as the template at registration and matches a freshly captured descriptor by Euclidean distance.
 * <p>
 * Fingerprint (optional second factor): without a hardware scanner the fingerprint is an uploaded image;
 * its keyed hash is stored and the same image must be presented again.
 */
@Service
public class BiometricService {

    public static final int DESCRIPTOR_SIZE = 128;
    private static final int MAX_FACE_IMAGE_CHARS = 2_000_000;

    public record FaceMatch(boolean matched, double distance) {
        /** Similarity score in percent, for display only. */
        public int score() {
            return (int) Math.round(Math.max(0, Math.min(1, 1 - distance)) * 100);
        }
    }

    private final CryptoService crypto;
    private final double faceThreshold;

    public BiometricService(CryptoService crypto, @Value("${evoting.biometric.face-threshold}") double faceThreshold) {
        this.crypto = crypto;
        this.faceThreshold = faceThreshold;
    }

    /** Parses "0.12,-0.03,..." into 128 numbers; returns null if it is not a valid descriptor. */
    public double[] parseDescriptor(String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = text.split(",");
        if (parts.length != DESCRIPTOR_SIZE) return null;
        double[] values = new double[DESCRIPTOR_SIZE];
        try {
            for (int i = 0; i < parts.length; i++) {
                values[i] = Double.parseDouble(parts[i].trim());
                if (!Double.isFinite(values[i])) return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return values;
    }

    /** Normalised text form of a descriptor for storage. */
    public String formatDescriptor(double[] descriptor) {
        StringBuilder sb = new StringBuilder();
        for (double v : descriptor) {
            if (sb.length() > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.6f", v));
        }
        return sb.toString();
    }

    public boolean isValidFaceImage(String dataUrl) {
        return dataUrl != null && dataUrl.length() <= MAX_FACE_IMAGE_CHARS
                && (dataUrl.startsWith("data:image/jpeg;base64,") || dataUrl.startsWith("data:image/png;base64,"))
                && dataUrl.substring(dataUrl.indexOf(',') + 1).matches("[A-Za-z0-9+/=]+");
    }

    public FaceMatch matchFace(Voter voter, double[] candidate) {
        double[] stored = parseDescriptor(voter.getFaceDescriptor());
        if (stored == null || candidate == null) return new FaceMatch(false, Double.MAX_VALUE);
        double sum = 0;
        for (int i = 0; i < DESCRIPTOR_SIZE; i++) {
            double d = stored[i] - candidate[i];
            sum += d * d;
        }
        double distance = Math.sqrt(sum);
        return new FaceMatch(distance <= faceThreshold, distance);
    }

    public String fingerprintTemplate(byte[] fingerprintImage) {
        return crypto.hmac("fingerprint|" + crypto.sha256(fingerprintImage));
    }

    public boolean matchFingerprint(Voter voter, byte[] fingerprintImage) {
        return voter.getFingerprintHash() != null
                && voter.getFingerprintHash().equals(fingerprintTemplate(fingerprintImage));
    }
}
