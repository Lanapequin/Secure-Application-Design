# Architecture Document — Secure App
## ECI Workshop: Arquitectura Segura con Certificados Digitales

**Author:** [Your Name]  
**Date:** 2024  
**Course:** Arquitectura de Software — Escuela Colombiana de Ingeniería Julio Garavito

---

## 1. Introduction

This document describes the design and security architecture of the Secure App, a multi-server web application built following the principles taught in the *Taller de Arquitectura Segura* workshop. The system is composed of three distinct servers, each with a specific responsibility, communicating exclusively over HTTPS/TLS encrypted channels.

The core challenge addressed by this architecture is: *how do you design a web system where integrity, authentication, and authorization are guaranteed at both the user level and the server level?*

---

## 2. System Overview

The application implements a classic three-tier architecture with an important addition: mutual authentication between backend servers.

```
                        ┌─────────────┐
                        │   Browser   │
                        └──────┬──────┘
                               │ HTTPS (TLS 1.2+)
                               │ Let's Encrypt Certificate
                               ▼
                   ┌───────────────────────┐
                   │   Server 1 — Apache   │
                   │   (Presentation Tier) │
                   │   HTML + CSS + JS     │
                   │   Port 443 / HTTPS    │
                   └───────┬───────────────┘
                           │ Async HTTPS (fetch API)
               ┌───────────┴────────────┐
               │                        │
               ▼                        ▼
  ┌────────────────────┐    ┌────────────────────────┐
  │  Server 2          │    │  Server 3              │
  │  Login Service     │    │  Backend Service       │
  │  (Logic Tier)      │    │  (Logic Tier)          │
  │  Spring 5 + Jetty  │    │  Spring 5 + Jetty      │
  │  Port 5000 / HTTPS │    │  Port 6000 / HTTPS     │
  │  KeyStore: PKCS12  │◀──▶│  KeyStore: PKCS12      │
  │  TrustStore: JKS   │mTLS│  TrustStore: JKS       │
  └────────────────────┘    └────────────────────────┘
```

---

## 3. Component Description

### 3.1 Server 1 — Apache Web Server (Presentation Tier)

**Responsibility:** Deliver the HTML/JavaScript single-page client application to the user's browser over an encrypted TLS connection.

**Technology:** Apache httpd on Amazon Linux 2023.

**Security:**
- TLS certificate issued by **Let's Encrypt** (a trusted public CA).
- All HTTP traffic on port 80 is redirected (301) to HTTPS port 443.
- Modern cipher suites only: `ECDHE-RSA-AES128-GCM-SHA256`, `ECDHE-RSA-AES256-GCM-SHA384`.
- `Strict-Transport-Security` header enforces HTTPS on subsequent visits.
- `X-Content-Type-Options: nosniff` prevents MIME-type sniffing attacks.
- `X-Frame-Options: DENY` prevents clickjacking.

**Role in 12-factor:** Serves static assets. The only file that changes between environments is `js/config.js`, which contains the backend server URLs.

---

### 3.2 Server 2 — Login Service (Authentication)

**Responsibility:** Authenticate users (login/register) and issue JWT tokens that authorize access to the backend service.

**Technology:** Spring Framework 5.3 + Spring Security 5.7 + Embedded Jetty 9.4.

**Endpoints:**
- `POST /api/auth/login` — validates credentials, returns a JWT token.
- `POST /api/auth/register` — creates a new user with a BCrypt-hashed password.
- `GET /health` — health check (public).

**Security mechanisms:**
1. **HTTPS/TLS:** Runs with a PKCS12 KeyStore. The server's identity is proved to clients via its certificate.
2. **Password hashing:** Spring Security's `BCryptPasswordEncoder` with default strength (10 rounds). Passwords are never stored or logged in plain text.
3. **JWT token generation:** HMAC-SHA256 signed tokens with 1-hour expiry. The signing secret is injected via environment variable `TOKEN_SECRET`.
4. **TrustStore:** A JKS TrustStore containing the Backend Service's certificate, enabling mutual TLS verification for server-to-server calls.
5. **CORS filter:** Restricts browser cross-origin requests to the configured `ALLOWED_ORIGIN` (the Apache server's domain).

---

### 3.3 Server 3 — Backend Service (Business Logic)

**Responsibility:** Provide secured RESTful API endpoints that serve business data, accessible only to authenticated users.

**Technology:** Spring Framework 5.3 + Embedded Jetty 9.4.

**Endpoints:**
- `GET /api/hello` — authenticated greeting.
- `GET /api/data` — protected data records.
- `GET /api/secure-info` — security configuration info.
- `GET /health` — health check (public).

**Security mechanisms:**
1. **HTTPS/TLS:** Own PKCS12 KeyStore with its own certificate.
2. **JWT validation filter:** `TokenAuthFilter` intercepts every `/api/*` request, extracts the `Authorization: Bearer <token>` header, validates the HMAC-SHA256 signature, checks expiry, and extracts the username. Rejects (401) any request with an invalid or missing token.
3. **Mutual TLS (mTLS):** TrustStore contains the Login Service's certificate. When the Backend Service receives a call from the Login Service, it can verify the caller's identity via the certificate chain.
4. **Stateless:** No server-side sessions. Every request is self-contained.

---

## 4. Security Architecture Deep Dive

### 4.1 TLS Certificate Chain

```
Production:
  Apache (Server 1)         → Let's Encrypt certificate (public CA)
  Login Service (Server 2)  → Self-signed PKCS12 (or Let's Encrypt in prod)
  Backend Service (Server 3)→ Self-signed PKCS12 (or Let's Encrypt in prod)

TrustStore relationships:
  Login Service TrustStore  ← contains Backend Service certificate
  Backend Service TrustStore← contains Login Service certificate
```

This means:
- The browser trusts Apache (via Let's Encrypt, already in browser's trust store).
- The browser can also access the Spring services (after accepting the self-signed cert, or with Let's Encrypt in production).
- Server 2 and Server 3 mutually verify each other via their TrustStores.

### 4.2 Authentication Flow

```
1.  Browser  ──POST /api/auth/login──▶ Login Service
             (username + password over HTTPS)

2.  Login Service:
    a. Loads user from store
    b. BCrypt.verify(submittedPassword, storedHash)
    c. If match → generate JWT(username, role, expiry=now+1h)
    d. Return { token: "..." }

3.  Browser stores token in sessionStorage

4.  Browser  ──GET /api/data──────────▶ Backend Service
             (Authorization: Bearer <token>)

5.  Backend Service:
    a. TokenAuthFilter.doFilter()
    b. Extract token from header
    c. Split: header.payload.signature
    d. HMAC-SHA256(header.payload, TOKEN_SECRET) == signature?
    e. Is exp timestamp in the future?
    f. If all pass → set authenticatedUser attribute → continue
    g. If any fail → 401 Unauthorized

6.  BackendController handles request, returns data
```

### 4.3 Password Security

Passwords are never stored in plain text. The BCrypt algorithm is used:

```
Registration:
  plainPassword → BCryptPasswordEncoder.encode() → $2a$10$xxx... (60 chars)
  Only the hash is stored.

Login verification:
  BCryptPasswordEncoder.matches(plainPassword, storedHash)
  This is a one-way check. The hash cannot be reversed.
```

BCrypt includes:
- A **random salt** (embedded in the hash string).
- A **cost factor** (10 by default), making brute-force attacks expensive.
- The same password produces different hashes each time it is encoded.

### 4.4 The Metaphor of the System (Metáfora del Sistema)

Think of this architecture as a **secure government building complex**:

- **Apache (Server 1)** is the **public entrance** — everyone can walk in. The door is made of glass (transparent HTTPS), but a guard (TLS) checks your identity before you enter. Let's Encrypt is the government-recognized locksmith who certified the lock.

- **Login Service (Server 2)** is the **security checkpoint** — like a passport control booth. You present your credentials (username + password). The officer checks your passport against the government records (BCrypt hash verification). If you pass, you receive a **temporary badge** (JWT token) that is stamped with an unforgeable official seal (HMAC-SHA256 signature).

- **Backend Service (Server 3)** is the **restricted area** — a room where classified documents are stored. The guard at the door only looks at your badge (JWT token). She doesn't ask for your password again. She checks: Is the seal authentic? Is the badge still valid (not expired)? Is this badge from our issuing office (matching TOKEN_SECRET)? If yes → you may enter.

- **mTLS between Server 2 and Server 3** is like the two guards knowing each other personally: when a guard from Server 2 calls Server 3 on the internal phone, Server 3 checks the voice/ID to confirm it is really Server 2 calling, not an impostor.

---

## 5. 12-Factor App Implementation

This project strictly follows the [12-factor methodology](https://12factor.net/):

| Factor | How it's applied in this project |
|---|---|
| **I. Codebase** | Single Git repo with all services. One repo, multiple deploys (dev, staging, prod). |
| **II. Dependencies** | All Java dependencies declared in `pom.xml`. No system-level implicit dependencies. |
| **III. Config** | `PORT`, `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `TOKEN_SECRET`, `ALLOWED_ORIGIN` — all from environment variables. No secrets in code. |
| **IV. Backing services** | Keystores and truststores are treated as attached resources referenced by path from env var. |
| **VI. Processes** | Both Spring services are stateless. No sticky sessions. Any instance can serve any request. |
| **VII. Port binding** | Services export themselves via a port read from `PORT` env var. No app server required. |
| **IX. Disposability** | Embedded Jetty starts in seconds. Systemd enables fast restart on crash (`Restart=always`). |
| **X. Dev/prod parity** | Same JARs are run locally and in AWS. Only env vars change. |
| **XI. Logs** | Both services log to stdout. Systemd captures logs; `journalctl` provides access. |

---

## 6. AWS Infrastructure

```
┌──────────────────────────────────────────────────────────────────────┐
│                            AWS Region                                │
│                                                                      │
│   ┌─────────────────────────────────────────────────────────────┐    │
│   │                        VPC                                   │    │
│   │                                                              │    │
│   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │    │
│   │  │  EC2: Apache │  │  EC2: Login  │  │  EC2: Backend    │  │    │
│   │  │  t2.micro    │  │  t2.micro    │  │  t2.micro        │  │    │
│   │  │  SG: 80,443  │  │  SG: 5000   │  │  SG: 6000        │  │    │
│   │  └──────────────┘  └──────────────┘  └──────────────────┘  │    │
│   │                                                              │    │
│   │  Security Groups restrict access:                            │    │
│   │    Instance 1: 80,443 from 0.0.0.0/0 (public)              │    │
│   │    Instance 2: 5000 from 0.0.0.0/0 (or restrict to SG1)    │    │
│   │    Instance 3: 6000 from 0.0.0.0/0 (or restrict to SG1/2)  │    │
│   └─────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 7. Security Guarantees

This architecture satisfies all workshop requirements:

| Requirement | Mechanism | Where |
|---|---|---|
| **User Integrity** | HTTPS ensures data is not tampered with in transit | All servers |
| **User Authentication** | BCrypt login + JWT token | Login Service |
| **User Authorization** | TokenAuthFilter on every protected route | Backend Service |
| **Server Integrity** | TLS certificates prove server identity | All servers |
| **Server Authentication** | Mutual TLS (TrustStore) | Server 2 ↔ Server 3 |
| **Server Authorization** | Only servers with trusted certs can call each other | mTLS |
| **2 Backend Servers** | Login Service (port 5000) + Backend Service (port 6000) | AWS |

---

## 8. GitHub Repository Structure

```
secure-app/
├── .gitignore                    # Excludes *.p12, *.jks, target/
├── docs/
│   ├── README.md                 # Deployment instructions
│   └── architecture.md           # This document
├── scripts/
│   ├── generate-certs.sh
│   ├── setup-apache.sh
│   └── deploy-aws.sh
├── login-service/
│   ├── pom.xml
│   └── src/
└── backend-service/
    ├── pom.xml
    └── src/
```

> **Important:** Keystore files (`*.p12`, `*.jks`) and the `target/` build directory are listed in `.gitignore` and must never be committed to version control. Secrets are injected via environment variables at runtime.

---

## 9. References

- Spring Framework 5.3 Documentation: https://docs.spring.io/spring-framework/docs/5.3.x/reference/html/
- Spring Security 5.7 Reference: https://docs.spring.io/spring-security/reference/5.7/
- Oracle keytool documentation: https://docs.oracle.com/en/java/javase/11/tools/keytool.html
- PKCS12 vs JKS: https://docs.oracle.com/cd/E19509-01/820-3503/ggffo/index.html
- Let's Encrypt: https://letsencrypt.org/getting-started/
- 12-factor App: https://12factor.net/
- AWS AL2023 LAMP Setup: https://docs.aws.amazon.com/linux/al2023/ug/ec2-lamp-amazon-linux-2023.html
- Workshop Reference: *Taller de Arquitectura Segura* by Luis Daniel Benavides Navarro
