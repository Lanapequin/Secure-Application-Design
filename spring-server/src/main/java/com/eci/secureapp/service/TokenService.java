package com.eci.secureapp.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Simple JWT-like token service using HMAC-SHA256.
 * In production use a proper JWT library (jjwt).
 * Secret is read from environment variable TOKEN_SECRET (12-factor principle).
 */
@Service
public class TokenService {

    private final String secret;

    public TokenService() {
        String envSecret = System.getenv("TOKEN_SECRET");
        this.secret = (envSecret != null && !envSecret.isEmpty())
                ? envSecret
                : "default-dev-secret-change-in-production-please-12345";
    }

    /**
     * Creates a simple token: base64(header).base64(payload).base64(signature)
     */
    public String generateToken(String username, String role) {
        String header = Base64.getUrlEncoder().encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        long expiry = System.currentTimeMillis() + 3600_000; // 1 hour
        String payloadJson = String.format(
                "{\"sub\":\"%s\",\"role\":\"%s\",\"exp\":%d}",
                username, role, expiry);
        String payload = Base64.getUrlEncoder().encodeToString(
                payloadJson.getBytes(StandardCharsets.UTF_8));

        String signature = sign(header + "." + payload);
        return header + "." + payload + "." + signature;
    }

    /**
     * Validates token and returns the username, or null if invalid/expired.
     */
    public String validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            String expectedSig = sign(parts[0] + "." + parts[1]);
            if (!expectedSig.equals(parts[2])) return null;

            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            // Extract expiry
            int expIdx = payloadJson.indexOf("\"exp\":");
            if (expIdx == -1) return null;
            long exp = Long.parseLong(payloadJson.substring(expIdx + 6,
                    payloadJson.indexOf("}", expIdx)));
            if (System.currentTimeMillis() > exp) return null;

            // Extract subject
            int subIdx = payloadJson.indexOf("\"sub\":\"");
            if (subIdx == -1) return null;
            int start = subIdx + 7;
            int end = payloadJson.indexOf("\"", start);
            return payloadJson.substring(start, end);

        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().encodeToString(sig);
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }
}
