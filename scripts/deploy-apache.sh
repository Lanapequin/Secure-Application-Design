#!/bin/bash
# =============================================================================
# deploy-apache.sh
# Configures Apache on Amazon Linux 2023 (AL2023) to:
#   1. Install and start Apache (httpd)
#   2. Install Certbot (Let's Encrypt) and obtain a TLS certificate
#   3. Deploy the HTML/JS client files to /var/www/html
#   4. Configure Apache Virtual Host for HTTPS
#   5. Enable auto-renewal of the certificate
#
# Run on: EC2 Instance 1 (Apache / Client server)
# OS: Amazon Linux 2023
#
# Prerequisites:
#   - EC2 instance with a public IPv4 address
#   - Security groups allow TCP 22 (SSH), 80 (HTTP), 443 (HTTPS)
#   - A domain name pointing to this instance's IP (needed for Let's Encrypt)
#     OR use the --standalone mode with port 80 temporarily open
#
# Usage:
#   chmod +x deploy-apache.sh
#   sudo ./deploy-apache.sh YOUR_DOMAIN.com
# =============================================================================

set -e

DOMAIN="${1:-}"
if [ -z "$DOMAIN" ]; then
  echo "Usage: sudo ./setup-apache.sh <your-domain.com>"
  echo "Example: sudo ./setup-apache.sh app.example.com"
  exit 1
fi

echo ""
echo "================================================================="
echo "  Setting up Apache + Let's Encrypt TLS for domain: $DOMAIN"
echo "================================================================="

# ─── Step 1: System update ────────────────────────────────────────────────────
echo ""
echo "=== [1/7] Updating system packages ==="
dnf upgrade -y
echo "System updated."

# ─── Step 2: Install Apache ───────────────────────────────────────────────────
echo ""
echo "=== [2/7] Installing Apache (httpd) ==="
dnf install -y httpd mod_ssl wget
systemctl start httpd
systemctl enable httpd
echo "Apache installed and started."

# Verify
systemctl is-active httpd && echo "   Apache is ACTIVE" || echo "Apache is NOT active!"

# ─── Step 3: Configure file permissions ───────────────────────────────────────
echo ""
echo "=== [3/7] Configuring /var/www permissions ==="
usermod -a -G apache ec2-user 2>/dev/null || true
chown -R ec2-user:apache /var/www
chmod 2775 /var/www
find /var/www -type d -exec chmod 2775 {} \;
find /var/www -type f -exec chmod 0664 {} \;
echo "Permissions set."

# ─── Step 4: Install Certbot (Let's Encrypt) ──────────────────────────────────
echo ""
echo "=== [4/7] Installing Certbot ==="
dnf install -y python3 augeas-libs
pip3 install --quiet certbot certbot-apache
echo "Certbot installed."

# ─── Step 5: Obtain Let's Encrypt Certificate ─────────────────────────────────
echo ""
echo "=== [5/7] Obtaining Let's Encrypt certificate for $DOMAIN ==="
echo "   (Apache must be running and domain must resolve to this server's IP)"

# Temporarily stop Apache so Certbot can use port 80 for verification,
# then restart it after. Alternatively, use --webroot if Apache stays up.
certbot certonly \
  --standalone \
  --non-interactive \
  --agree-tos \
  --register-unsafely-without-email \
  --domain "$DOMAIN" \
  --pre-hook  "systemctl stop httpd" \
  --post-hook "systemctl start httpd"

echo "Certificate obtained."
echo "Certificate: /etc/letsencrypt/live/$DOMAIN/fullchain.pem"
echo "Private key: /etc/letsencrypt/live/$DOMAIN/privkey.pem"

# ─── Step 6: Deploy HTML/JS Client Files ──────────────────────────────────────
echo ""
echo "=== [6/7] Deploying client files to /var/www/html ==="

# If running from project root, copy client files
if [ -d "../apache-client" ]; then
  cp -r ../apache-client/html/* /var/www/html/
  cp -r ../apache-client/css  /var/www/html/
  cp -r ../apache-client/js   /var/www/html/
  echo "Client files copied from ../apache-client"
else
  echo "../apache-client not found — copy files manually to /var/www/html"
fi

# ─── Step 7: Configure Apache Virtual Host with HTTPS ─────────────────────────
echo ""
echo "=== [7/7] Writing Apache Virtual Host configuration ==="

cat > /etc/httpd/conf.d/secure-app.conf << EOF
# ── HTTP → HTTPS redirect ──────────────────────────────────────────────────
<VirtualHost *:80>
    ServerName $DOMAIN
    DocumentRoot /var/www/html
    # Redirect all HTTP traffic to HTTPS
    RewriteEngine On
    RewriteRule ^(.*)$ https://%{HTTP_HOST}\$1 [R=301,L]
</VirtualHost>

# ── HTTPS Virtual Host ─────────────────────────────────────────────────────
<VirtualHost *:443>
    ServerName $DOMAIN
    DocumentRoot /var/www/html

    # Let's Encrypt certificate (auto-renewed)
    SSLEngine on
    SSLCertificateFile      /etc/letsencrypt/live/$DOMAIN/fullchain.pem
    SSLCertificateKeyFile   /etc/letsencrypt/live/$DOMAIN/privkey.pem

    # Modern TLS settings (disable old/weak protocols)
    SSLProtocol             all -SSLv3 -TLSv1 -TLSv1.1
    SSLCipherSuite          ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384
    SSLHonorCipherOrder     off
    SSLSessionTickets       off

    # Security headers
    Header always set Strict-Transport-Security "max-age=63072000; includeSubDomains; preload"
    Header always set X-Content-Type-Options    "nosniff"
    Header always set X-Frame-Options           "DENY"
    Header always set X-XSS-Protection          "1; mode=block"
    Header always set Referrer-Policy           "strict-origin-when-cross-origin"

    # Serve static files
    <Directory /var/www/html>
        Options -Indexes +FollowSymLinks
        AllowOverride All
        Require all granted
    </Directory>

    # Cache control for JS/CSS
    <FilesMatch "\.(js|css)$">
        Header set Cache-Control "max-age=3600, public"
    </FilesMatch>

    ErrorLog  /var/log/httpd/secure-app-error.log
    CustomLog /var/log/httpd/secure-app-access.log combined
</VirtualHost>
EOF

echo "Virtual host configuration written."

# Enable mod_headers and mod_rewrite
echo "LoadModule headers_module modules/mod_headers.so"  >> /etc/httpd/conf/httpd.conf 2>/dev/null || true
echo "LoadModule rewrite_module modules/mod_rewrite.so"  >> /etc/httpd/conf/httpd.conf 2>/dev/null || true

# Test config
echo ""
echo "=== Testing Apache configuration ==="
httpd -t && echo "Apache configuration is VALID" || (echo " Apache configuration has errors!" && exit 1)

# Reload Apache
systemctl reload httpd
echo "Apache reloaded."

# ─── Auto-renewal cron job ────────────────────────────────────────────────────
echo ""
echo "=== Setting up auto-renewal cron ==="
# Let's Encrypt certs expire in 90 days; renew at day 60
(crontab -l 2>/dev/null; echo "0 0,12 * * * certbot renew --quiet --pre-hook 'systemctl stop httpd' --post-hook 'systemctl start httpd'") | crontab -
echo "Auto-renewal cron job added (runs twice daily)."

# ─── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo "================================================================="
echo "     Apache + Let's Encrypt setup COMPLETE"
echo "================================================================="
echo ""
echo "  Your client is now accessible at: https://$DOMAIN"
echo ""
echo "  Certificate details:"
certbot certificates 2>/dev/null | grep -E "(Domains|Expiry)"
echo ""
echo "  Next steps:"
echo "    1. Update apache-client/js/config.js with your server IPs/domains"
echo "    2. Start the Login Service on EC2 Instance 2 (port 5000)"
echo "    3. Start the Backend Service on EC2 Instance 3 (port 6000)"
echo "    4. Run deploy-aws.sh for the Spring services"
echo "================================================================="
