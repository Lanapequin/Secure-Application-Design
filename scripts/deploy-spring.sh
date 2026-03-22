#!/bin/bash
# =============================================================================
# deploy-spring.sh
# Deploys the Login Service and Backend Service to their respective EC2
# instances using SSH/SCP.
#
# Architecture:
#   EC2 Instance 1 (Apache)        → serves HTML/JS client over HTTPS
#   EC2 Instance 2 (Login Service) → Spring + HTTPS on port 5000
#   EC2 Instance 3 (Backend Svc)   → Spring + HTTPS on port 6000
#
# Prerequisites:
#   - Maven installed locally: mvn --version
#   - Java 11+ installed locally: java -version
#   - SSH key pair for EC2 (.pem file)
#   - EC2 instances running Amazon Linux 2023
#   - Security groups:
#       Instance 1: ports 22, 80, 443
#       Instance 2: ports 22, 5000
#       Instance 3: ports 22, 6000
#   - generate-certs.sh must have been run before this script
#
# Usage:
#   chmod +x deploy-spring.sh
#   ./deploy-spring.sh \
#     --key ~/.ssh/my-key.pem \
#     --login-ip 1.2.3.4 \
#     --backend-ip 5.6.7.8
# =============================================================================

set -e

# ─── Parse arguments ──────────────────────────────────────────────────────────
KEY_FILE=""
LOGIN_IP=""
BACKEND_IP=""
SSH_USER="ec2-user"
LOGIN_PORT="5000"
BACKEND_PORT="6000"

while [[ $# -gt 0 ]]; do
  case $1 in
    --key)       KEY_FILE="$2";    shift 2 ;;
    --login-ip)  LOGIN_IP="$2";   shift 2 ;;
    --backend-ip) BACKEND_IP="$2"; shift 2 ;;
    --user)      SSH_USER="$2";   shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [ -z "$KEY_FILE" ] || [ -z "$LOGIN_IP" ] || [ -z "$BACKEND_IP" ]; then
  echo "❌ Usage: ./deploy-aws.sh --key <pem-file> --login-ip <ip> --backend-ip <ip>"
  exit 1
fi

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo ""
echo "================================================================="
echo "  Secure App — AWS Deployment"
echo "================================================================="
echo "  Key file   : $KEY_FILE"
echo "  Login IP   : $LOGIN_IP (port $LOGIN_PORT)"
echo "  Backend IP : $BACKEND_IP (port $BACKEND_PORT)"
echo "  Project    : $PROJECT_ROOT"
echo "================================================================="

# ─── Helper functions ─────────────────────────────────────────────────────────
ssh_cmd() {
  local host="$1"; shift
  ssh -i "$KEY_FILE" -o StrictHostKeyChecking=no "$SSH_USER@$host" "$@"
}

scp_file() {
  local src="$1"
  local host="$2"
  local dst="$3"
  scp -i "$KEY_FILE" -o StrictHostKeyChecking=no -r "$src" "$SSH_USER@$host:$dst"
}

install_java() {
  local host="$1"
  echo "   Installing Java 11 on $host..."
  ssh_cmd "$host" "
    sudo dnf update -y &&
    sudo dnf install -y java-11-amazon-corretto &&
    java -version
  "
  echo "   ✅ Java 11 installed."
}

create_systemd_service() {
  local host="$1"
  local service_name="$2"
  local jar_path="$3"
  local env_vars="$4"
  local description="$5"

  echo "   Creating systemd service: $service_name"
  ssh_cmd "$host" "
cat << 'UNIT_EOF' | sudo tee /etc/systemd/system/${service_name}.service
[Unit]
Description=$description
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/$service_name
ExecStart=/usr/bin/java -jar $jar_path
Restart=always
RestartSec=10
$env_vars

# Security hardening
NoNewPrivileges=yes
ProtectSystem=full
PrivateTmp=yes

[Install]
WantedBy=multi-user.target
UNIT_EOF

    sudo systemctl daemon-reload
    sudo systemctl enable $service_name
  "
}

# ─── Step 1: Build both services ──────────────────────────────────────────────
echo ""
echo "=== [1/6] Building Maven projects ==="

echo "   Building login-service..."
cd "$PROJECT_ROOT/login-service"
mvn clean package -q -DskipTests
LOGIN_JAR=$(ls target/*-fat.jar 2>/dev/null || ls target/*.jar | head -1)
echo "   ✅ Login JAR: $LOGIN_JAR"

echo "   Building backend-service..."
cd "$PROJECT_ROOT/backend-service"
mvn clean package -q -DskipTests
BACKEND_JAR=$(ls target/*-fat.jar 2>/dev/null || ls target/*.jar | head -1)
echo "   ✅ Backend JAR: $BACKEND_JAR"

cd "$PROJECT_ROOT"

# ─── Step 2: Install Java on both instances ───────────────────────────────────
echo ""
echo "=== [2/6] Installing Java 11 on EC2 instances ==="
install_java "$LOGIN_IP"
install_java "$BACKEND_IP"

# ─── Step 3: Deploy Login Service ─────────────────────────────────────────────
echo ""
echo "=== [3/6] Deploying Login Service to $LOGIN_IP ==="

ssh_cmd "$LOGIN_IP" "sudo mkdir -p /opt/login-service/keystore && sudo chown -R ec2-user:ec2-user /opt/login-service"

# Copy JAR
scp_file "$PROJECT_ROOT/login-service/$LOGIN_JAR" "$LOGIN_IP" "/opt/login-service/app.jar"

# Copy keystore files
scp_file "$PROJECT_ROOT/login-service/src/main/resources/keystore/." "$LOGIN_IP" "/opt/login-service/keystore/"

echo "   ✅ Login Service files deployed."

# Create systemd service with env vars
create_systemd_service \
  "$LOGIN_IP" \
  "login-service" \
  "/opt/login-service/app.jar" \
  "Environment=PORT=$LOGIN_PORT
Environment=KEYSTORE_PATH=/opt/login-service/keystore/loginkeystore.p12
Environment=KEYSTORE_PASSWORD=changeit
Environment=KEYSTORE_ALIAS=loginkeypair
Environment=TRUSTSTORE_PATH=/opt/login-service/keystore/myTrustStore
Environment=TRUSTSTORE_PASSWORD=changeit
Environment=TOKEN_SECRET=\$(openssl rand -hex 32)
Environment=ALLOWED_ORIGIN=*" \
  "ECI Login Service"

ssh_cmd "$LOGIN_IP" "sudo systemctl start login-service && sudo systemctl status login-service --no-pager"
echo "   ✅ Login Service started on port $LOGIN_PORT."

# ─── Step 4: Deploy Backend Service ───────────────────────────────────────────
echo ""
echo "=== [4/6] Deploying Backend Service to $BACKEND_IP ==="

ssh_cmd "$BACKEND_IP" "sudo mkdir -p /opt/backend-service/keystore && sudo chown -R ec2-user:ec2-user /opt/backend-service"

scp_file "$PROJECT_ROOT/backend-service/$BACKEND_JAR" "$BACKEND_IP" "/opt/backend-service/app.jar"
scp_file "$PROJECT_ROOT/backend-service/src/main/resources/keystore/." "$BACKEND_IP" "/opt/backend-service/keystore/"

echo "   ✅ Backend Service files deployed."

create_systemd_service \
  "$BACKEND_IP" \
  "backend-service" \
  "/opt/backend-service/app.jar" \
  "Environment=PORT=$BACKEND_PORT
Environment=KEYSTORE_PATH=/opt/backend-service/keystore/backendkeystore.p12
Environment=KEYSTORE_PASSWORD=changeit
Environment=KEYSTORE_ALIAS=backendkeypair
Environment=TRUSTSTORE_PATH=/opt/backend-service/keystore/myTrustStore
Environment=TRUSTSTORE_PASSWORD=changeit
Environment=TOKEN_SECRET=REPLACE_WITH_SAME_SECRET_AS_LOGIN_SERVICE
Environment=ALLOWED_ORIGIN=*" \
  "ECI Backend Service"

ssh_cmd "$BACKEND_IP" "sudo systemctl start backend-service && sudo systemctl status backend-service --no-pager"
echo "   ✅ Backend Service started on port $BACKEND_PORT."

# ─── Step 5: Open firewall ports ──────────────────────────────────────────────
echo ""
echo "=== [5/6] Opening firewall ports ==="

for HOST in "$LOGIN_IP" "$BACKEND_IP"; do
  ssh_cmd "$HOST" "
    sudo firewall-cmd --permanent --add-port=5000/tcp 2>/dev/null || true
    sudo firewall-cmd --permanent --add-port=6000/tcp 2>/dev/null || true
    sudo firewall-cmd --reload 2>/dev/null || true
  "
done
echo "   ✅ Ports opened (if firewalld is active)."
echo "   ⚠️  Also verify AWS Security Groups allow these ports in the console."

# ─── Step 6: Health check ─────────────────────────────────────────────────────
echo ""
echo "=== [6/6] Health checks ==="
sleep 5  # Give services a moment to start

echo "   Checking Login Service health..."
ssh_cmd "$LOGIN_IP" "curl -sk https://localhost:$LOGIN_PORT/health | python3 -m json.tool" || \
  echo "   ⚠️  Login Service health check failed — check logs with: sudo journalctl -u login-service -n 50"

echo "   Checking Backend Service health..."
ssh_cmd "$BACKEND_IP" "curl -sk https://localhost:$BACKEND_PORT/health | python3 -m json.tool" || \
  echo "   ⚠️  Backend Service health check failed — check logs with: sudo journalctl -u backend-service -n 50"

# ─── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo "================================================================="
echo "  ✅ Deployment COMPLETE"
echo "================================================================="
echo ""
echo "  Login Service:   https://$LOGIN_IP:$LOGIN_PORT"
echo "  Backend Service: https://$BACKEND_IP:$BACKEND_PORT"
echo ""
echo "  ⚠️  IMPORTANT — Update config.js with real host addresses:"
echo "     LOGIN_SERVICE_URL:   \"https://$LOGIN_IP:$LOGIN_PORT\""
echo "     BACKEND_SERVICE_URL: \"https://$BACKEND_IP:$BACKEND_PORT\""
echo ""
echo "  ⚠️  IMPORTANT — Set the SAME TOKEN_SECRET on both services!"
echo "     Generate one with: openssl rand -hex 32"
echo ""
echo "  Useful commands:"
echo "    View login service logs  : ssh -i $KEY_FILE $SSH_USER@$LOGIN_IP  'sudo journalctl -u login-service -f'"
echo "    View backend service logs: ssh -i $KEY_FILE $SSH_USER@$BACKEND_IP 'sudo journalctl -u backend-service -f'"
echo "    Restart login service    : ssh -i $KEY_FILE $SSH_USER@$LOGIN_IP  'sudo systemctl restart login-service'"
echo "    Restart backend service  : ssh -i $KEY_FILE $SSH_USER@$BACKEND_IP 'sudo systemctl restart backend-service'"
echo "================================================================="
