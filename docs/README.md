# 🔐 Secure App — ECI Arquitectura Segura

A fully secured multi-server web application built with **pure Spring 5 + Maven** (no Spring Boot), deployed on AWS. Implements TLS encryption, JWT authentication, BCrypt password hashing, mutual TLS between servers, and follows 12-factor app principles.

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         User's Browser                              │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ HTTPS (Let's Encrypt cert)
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│              EC2 Instance 1 — Apache Server (port 443)              │
│   Serves: HTML + CSS + JavaScript (async client)                    │
│   TLS: Let's Encrypt certificate                                     │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ async fetch() over HTTPS
          ┌────────────────────┴─────────────────────┐
          │                                          │
          ▼ HTTPS (self-signed / LE cert)            ▼ HTTPS (self-signed / LE cert)
┌─────────────────────────┐              ┌──────────────────────────────┐
│  EC2 Instance 2          │              │  EC2 Instance 3              │
│  Login Service           │              │  Backend Service             │
│  Spring 5 + Jetty        │              │  Spring 5 + Jetty            │
│  Port: 5000              │   JWT token  │  Port: 6000                  │
│                          │─────────────▶│  (validated by TokenFilter)  │
│  /api/auth/login         │              │  /api/hello                  │
│  /api/auth/register      │              │  /api/data                   │
│  /health                 │              │  /api/secure-info            │
│                          │              │  /health                     │
│  KeyStore: PKCS12        │              │  KeyStore: PKCS12            │
│  TrustStore: JKS         │◀────mTLS────▶│  TrustStore: JKS             │
└─────────────────────────┘              └──────────────────────────────┘
```

---

## 🔒 Security Features

| Feature | Implementation |
|---|---|
| Transport Encryption | HTTPS/TLS on all 3 servers |
| Certificate Management | Let's Encrypt (Apache) + keytool PKCS12 (Spring) |
| Password Storage | BCrypt hashing (Spring Security) — never plain text |
| Authentication | JWT tokens (HMAC-SHA256) |
| Authorization | Token validation filter on all `/api/*` routes |
| Server-to-Server Auth | Mutual TLS (mTLS) with TrustStore |
| Config Management | 12-factor: all secrets in environment variables |
| Session | Stateless (JWT), stored in sessionStorage |

---

## 📁 Project Structure

```
secure-app/
├── login-service/                  # Spring server 1 — Authentication
│   ├── pom.xml
│   └── src/main/java/com/eci/security/
│       ├── Main.java               # Embedded Jetty + HTTPS
│       ├── config/
│       │   ├── AppConfig.java      # Spring MVC config
│       │   ├── SecurityConfig.java # Spring Security + BCrypt
│       │   └── CorsFilter.java     # CORS for Apache client
│       ├── model/
│       │   ├── User.java
│       │   └── LoginRequest.java
│       ├── service/
│       │   ├── UserDetailsServiceImpl.java  # BCrypt user store
│       │   └── TokenService.java   # JWT generation/validation
│       └── controller/
│           └── AuthController.java # /api/auth/login, /register
│
├── backend-service/                # Spring server 2 — REST APIs
│   ├── pom.xml
│   └── src/main/java/com/eci/backend/
│       ├── Main.java               # Embedded Jetty + HTTPS + mTLS
│       ├── config/
│       │   ├── AppConfig.java
│       │   ├── CorsFilter.java
│       │   └── TokenAuthFilter.java  # JWT validation on /api/*
│       └── controller/
│           └── BackendController.java  # /api/hello, /data, /secure-info
│
├── apache-client/                  # Static HTML/JS served by Apache
│   ├── html/index.html             # Single-page async application
│   ├── css/styles.css
│   └── js/
│       ├── config.js               # Server URLs (12-factor config)
│       └── app.js                  # Async fetch logic
│
├── scripts/
│   ├── generate-certs.sh           # keytool commands for all keystores
│   ├── setup-apache.sh             # Apache + Let's Encrypt on AL2023
│   └── deploy-aws.sh               # Build + deploy to EC2 via SSH
│
└── docs/
    ├── README.md                   # This file
    └── architecture.md             # Detailed architecture document
```

---

## ⚡ Quick Start (Local Development)

### Prerequisites

- Java 11+: `java -version`
- Maven 3.8+: `mvn -version`
- Java keytool (included with JDK)

### 1. Generate certificates

```bash
cd scripts/
chmod +x generate-certs.sh
./generate-certs.sh
```

This creates:
- `login-service/src/main/resources/keystore/loginkeystore.p12`
- `login-service/src/main/resources/keystore/myTrustStore`
- `backend-service/src/main/resources/keystore/backendkeystore.p12`
- `backend-service/src/main/resources/keystore/myTrustStore`

### 2. Build both services

```bash
# Terminal 1
cd login-service/
mvn clean package -DskipTests

# Terminal 2
cd backend-service/
mvn clean package -DskipTests
```

### 3. Start Login Service (port 5000)

```bash
cd login-service/

# Set environment variables (12-factor principle)
export PORT=5000
export KEYSTORE_PATH=src/main/resources/keystore/loginkeystore.p12
export KEYSTORE_PASSWORD=changeit
export KEYSTORE_ALIAS=loginkeypair
export TRUSTSTORE_PATH=src/main/resources/keystore/myTrustStore
export TRUSTSTORE_PASSWORD=changeit
export TOKEN_SECRET=my-super-secret-dev-key-change-in-prod
export ALLOWED_ORIGIN=*

java -jar target/*-fat.jar
```

### 4. Start Backend Service (port 6000)

```bash
cd backend-service/

export PORT=6000
export KEYSTORE_PATH=src/main/resources/keystore/backendkeystore.p12
export KEYSTORE_PASSWORD=changeit
export KEYSTORE_ALIAS=backendkeypair
export TRUSTSTORE_PATH=src/main/resources/keystore/myTrustStore
export TRUSTSTORE_PASSWORD=changeit
export TOKEN_SECRET=my-super-secret-dev-key-change-in-prod   # SAME as login service
export ALLOWED_ORIGIN=*

java -jar target/*-fat.jar
```

### 5. Configure and open the client

Edit `apache-client/js/config.js` and uncomment the localhost URLs:

```js
LOGIN_SERVICE_URL:   "https://localhost:5000",
BACKEND_SERVICE_URL: "https://localhost:6000",
```

Then open `apache-client/html/index.html` in your browser.

> **Note:** Your browser will show a security warning for the self-signed certificate. Click "Advanced → Proceed" to continue. In production, Let's Encrypt certificates avoid this.

**Default credentials:**
- `admin` / `Admin123!`
- `user1` / `User123!`

---

## ☁️ AWS Deployment

### EC2 Instance Requirements

| Instance | Type | Ports | Purpose |
|---|---|---|---|
| Instance 1 | t2.micro | 22, 80, 443 | Apache + HTML/JS Client |
| Instance 2 | t2.micro | 22, 5000 | Login Service |
| Instance 3 | t2.micro | 22, 6000 | Backend Service |

All instances should run **Amazon Linux 2023 (AL2023)**.

### Step 1: Setup Apache (Instance 1)

```bash
# SSH into Instance 1
ssh -i your-key.pem ec2-user@<INSTANCE_1_IP>

# Upload and run setup script
scp -i your-key.pem scripts/setup-apache.sh ec2-user@<INSTANCE_1_IP>:~
ssh -i your-key.pem ec2-user@<INSTANCE_1_IP> "chmod +x setup-apache.sh && sudo ./setup-apache.sh your-domain.com"
```

### Step 2: Generate production certificates

For production, re-run `generate-certs.sh` with your real EC2 hostnames:

```bash
# Edit generate-certs.sh and set:
LOGIN_HOST="<INSTANCE_2_PUBLIC_DNS>"
BACKEND_HOST="<INSTANCE_3_PUBLIC_DNS>"

./generate-certs.sh
```

### Step 3: Deploy Spring services

```bash
chmod +x scripts/deploy-aws.sh
./scripts/deploy-aws.sh \
  --key ~/.ssh/your-key.pem \
  --login-ip <INSTANCE_2_IP> \
  --backend-ip <INSTANCE_3_IP>
```

### Step 4: Update client config

Edit `apache-client/js/config.js`:

```js
LOGIN_SERVICE_URL:   "https://<INSTANCE_2_IP>:5000",
BACKEND_SERVICE_URL: "https://<INSTANCE_3_IP>:6000",
```

Upload updated files to Instance 1:

```bash
scp -i your-key.pem -r apache-client/* ec2-user@<INSTANCE_1_IP>:/var/www/html/
```

### Step 5: Set the same TOKEN_SECRET on both services

```bash
# Generate a random secret
SECRET=$(openssl rand -hex 32)
echo $SECRET

# Set on Instance 2 (login-service)
ssh -i your-key.pem ec2-user@<INSTANCE_2_IP> \
  "sudo systemctl edit login-service --force"
# Add: Environment=TOKEN_SECRET=<your-secret>

# Set on Instance 3 (backend-service) — SAME secret
ssh -i your-key.pem ec2-user@<INSTANCE_3_IP> \
  "sudo systemctl edit backend-service --force"
# Add: Environment=TOKEN_SECRET=<your-secret>

# Restart both services
ssh -i your-key.pem ec2-user@<INSTANCE_2_IP> "sudo systemctl restart login-service"
ssh -i your-key.pem ec2-user@<INSTANCE_3_IP> "sudo systemctl restart backend-service"
```

---

## 🧪 Testing the API Manually

```bash
# Health checks (no auth needed)
curl -sk https://localhost:5000/health | python3 -m json.tool
curl -sk https://localhost:6000/health | python3 -m json.tool

# Login and get a token
TOKEN=$(curl -sk -X POST https://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "Token: $TOKEN"

# Call protected backend endpoints with the token
curl -sk https://localhost:6000/api/hello \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

curl -sk https://localhost:6000/api/data \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

curl -sk https://localhost:6000/api/secure-info \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

---

## 🌿 12-Factor App Compliance

This project follows the [12-factor methodology](https://12factor.net/):

| Factor | Implementation |
|---|---|
| I. Codebase | Single Git repo, tracked in GitHub |
| II. Dependencies | All declared in `pom.xml`, no implicit deps |
| III. Config | All secrets/URLs in environment variables |
| IV. Backing services | Keystore files treated as attached resources |
| VI. Processes | Stateless — JWT-based, no server-side sessions |
| VII. Port binding | Port from `PORT` env variable |
| IX. Disposability | Fast startup with embedded Jetty |
| XI. Logs | Written to stdout (captured by systemd) |

---

## 📝 Environment Variables Reference

### Login Service

| Variable | Default | Description |
|---|---|---|
| `PORT` | `5000` | HTTPS port |
| `KEYSTORE_PATH` | `src/.../loginkeystore.p12` | Path to PKCS12 keystore |
| `KEYSTORE_PASSWORD` | `changeit` | Keystore password |
| `KEYSTORE_ALIAS` | `loginkeypair` | Certificate alias |
| `TRUSTSTORE_PATH` | `src/.../myTrustStore` | Path to JKS truststore |
| `TRUSTSTORE_PASSWORD` | `changeit` | Truststore password |
| `TOKEN_SECRET` | (dev fallback) | **Change in production!** |
| `ALLOWED_ORIGIN` | `*` | CORS allowed origin |

### Backend Service

| Variable | Default | Description |
|---|---|---|
| `PORT` | `6000` | HTTPS port |
| `KEYSTORE_PATH` | `src/.../backendkeystore.p12` | Path to PKCS12 keystore |
| `KEYSTORE_PASSWORD` | `changeit` | Keystore password |
| `KEYSTORE_ALIAS` | `backendkeypair` | Certificate alias |
| `TRUSTSTORE_PATH` | `src/.../myTrustStore` | Path to JKS truststore |
| `TRUSTSTORE_PASSWORD` | `changeit` | Truststore password |
| `TOKEN_SECRET` | (dev fallback) | **Must match login-service!** |
| `ALLOWED_ORIGIN` | `*` | CORS allowed origin |

---

## 🛠️ Troubleshooting

**Browser says "Not Secure" for Spring services:**
In local dev this is expected (self-signed certs). Open `https://localhost:5000/health` and click "Proceed" to add a security exception, then open `https://localhost:6000/health` and do the same. Now the client can call them.

**`Connection refused` on port 5000/6000:**
Check service is running: `sudo systemctl status login-service`
Check logs: `sudo journalctl -u login-service -n 100`

**`401 Unauthorized` on backend calls:**
Ensure `TOKEN_SECRET` is identical on both services.

**Certificate errors between services:**
Ensure TrustStores contain the other service's certificate. Re-run `generate-certs.sh`.
