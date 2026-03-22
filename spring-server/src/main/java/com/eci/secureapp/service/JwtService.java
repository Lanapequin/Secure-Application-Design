package com.eci.secureapp.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Creates and validates JWT tokens signed with HMAC-SHA256.
 *
 * TOKEN_SECRET is read from environment variable so it is never
 * hardcoded in the repository (12-factor principle III).
 *
 * Token lifetime: 2 hours.
 */
@Service
public class JwtService {

    private final String secret;
    private static final long TOKEN_TTL_MS = 2 * 60 * 60 * 1000L; // 2 hours

    public JwtService() {
        String env = System.getenv("TOKEN_SECRET");
        this.secret = (env != null && !env.isBlank())
                ? env
                : "dev-secret-change-in-production-eci-2024";
    }

    /** Creates a signed JWT for the given user */
    public String generate(String username, String role) {
        String header  = b64(("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"));
        long   expiry  = System.currentTimeMillis() + TOKEN_TTL_MS;
        String payload = b64(String.format(
                "{\"sub\":\"%s\",\"role\":\"%s\",\"exp\":%d}",
                username, role, expiry));
        String sig = sign(header + "." + payload);
        return header + "." + payload + "." + sig;
    }

    /** Returns the username if the token is valid, null otherwise */
    public String validate(String token) {
        try {
            String[] p = token.split("\\.");
            if (p.length != 3) return null;
            if (!sign(p[0] + "." + p[1]).equals(p[2])) return null;

            String payload = new String(
                    Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);

            int ei = payload.indexOf("\"exp\":");
            long exp = Long.parseLong(
                    payload.substring(ei + 6, payload.indexOf("}", ei)).trim());
            if (System.currentTimeMillis() > exp) return null;

            int si    = payload.indexOf("\"sub\":\"");
            int start = si + 7;
            return payload.substring(start, payload.indexOf("\"", start));

        } catch (Exception e) { return null; }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                     .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
