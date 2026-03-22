package com.eci.secureapp.config;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * CORS filter so the browser client (served by Apache on Server 1)
 * can make cross-origin requests to this Spring server (Server 2).
 *
 * ALLOWED_ORIGIN is read from an environment variable (12-factor).
 * In production set it to: https://your-apache-domain.com
 */
public class CorsFilter implements Filter {

    @Override
    public void init(FilterConfig fc) {}

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = System.getenv("ALLOWED_ORIGIN");
        if (origin == null || origin.isBlank()) origin = "*";

        response.setHeader("Access-Control-Allow-Origin",      origin);
        response.setHeader("Access-Control-Allow-Methods",     "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers",
                           "Content-Type, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Max-Age",           "3600");

        // Respond immediately to preflight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(req, res);
    }

    @Override
    public void destroy() {}
}
