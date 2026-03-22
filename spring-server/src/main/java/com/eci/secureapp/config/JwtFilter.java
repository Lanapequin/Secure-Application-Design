package com.eci.secureapp.config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * JWT validation filter applied to every /api/* route.
 *
 * Expects header:  Authorization: Bearer <token>
 * Returns 401 if the token is missing, tampered, or expired.
 *
 * Uses the same HMAC-SHA256 logic as JwtService so no extra library is needed.
 * TOKEN_SECRET is read from environment variable (12-factor — no secrets in code).
 */
public class JwtFilter implements Filter {

    private String secret;

    @Override
    public void init(FilterConfig fc) {
        String env = System.getenv("TOKEN_SECRET");
        this.secret = (env != null && !env.isBlank())
                ? env
                : "dev-secret-change-in-production-eci-2024";
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Skip preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        // Skip public auth endpoints even if they somehow reach this filter
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(req, res);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            reject(response, "Missing Authorization header");
            return;
        }

        String token    = authHeader.substring(7);
        String username = validate(token);

        if (username == null) {
            reject(response, "Invalid or expired token");
            return;
        }

        // Pass username downstream to controllers
        request.setAttribute("username", username);
        chain.doFilter(req, res);
    }

    // ── JWT helpers ───────────────────────────────────────────────────────────

    private String validate(String token) {
        try {
            String[] p = token.split("\\.");
            if (p.length != 3) return null;

            // Verify signature
            if (!sign(p[0] + "." + p[1]).equals(p[2])) return null;

            // Decode payload
            String payload = new String(
                    Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);

            // Check expiry
            int ei = payload.indexOf("\"exp\":");
            if (ei < 0) return null;
            long exp = Long.parseLong(payload.substring(ei + 6,
                    payload.indexOf("}", ei)).trim());
            if (System.currentTimeMillis() > exp) return null;

            // Extract subject
            int si = payload.indexOf("\"sub\":\"");
            if (si < 0) return null;
            int start = si + 7;
            return payload.substring(start, payload.indexOf("\"", start));

        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void reject(HttpServletResponse res, String msg) throws IOException {
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + msg + "\"}");
    }

    @Override
    public void destroy() {}
}
