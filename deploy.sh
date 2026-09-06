#!/bin/bash
# ==============================================================================
# 🚀 LocNova (LocationSpoofer) - 1-Click VPS Production Installer Script
# ==============================================================================
# Target OS: Ubuntu 20.04 / 22.04 / 24.04 LTS or Debian 11/12
# Features Installed:
#   - Node.js 20.x LTS & npm
#   - PM2 Process Manager (Auto-restart on boot)
#   - Nginx Reverse Proxy with WebSocket (WSS) Support
#   - Certbot Let's Encrypt Free SSL Certificate
#   - UFW Firewall & Security Hardening
# ==============================================================================

set -e

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}====================================================${NC}"
echo -e "${GREEN}   🌐 LocNova (LocationSpoofer) VPS Installer      ${NC}"
echo -e "${BLUE}====================================================${NC}"

# Check if running as root
if [ "$EUID" -ne 0 ]; then
  echo -e "${RED}❌ Please run this script as root (e.g., sudo bash deploy.sh)${NC}"
  exit 1
fi

# Ask for domain or IP
read -p "Enter your VPS Domain Name (e.g. api.mydomain.com) or press ENTER to use Server IP: " DOMAIN_OR_IP
if [ -z "$DOMAIN_OR_IP" ]; then
    DOMAIN_OR_IP=$(curl -s ifconfig.me)
    IS_IP=true
    echo -e "${YELLOW}ℹ️ No domain entered. Using Public IP: $DOMAIN_OR_IP${NC}"
else
    IS_IP=false
fi

read -p "Enter Admin Email for SSL / System Alerts: " ADMIN_EMAIL
if [ -z "$ADMIN_EMAIL" ]; then
    ADMIN_EMAIL="admin@$DOMAIN_OR_IP"
fi

echo -e "\n${GREEN}[1/6] 📦 Updating System Packages & Installing Dependencies...${NC}"
apt-get update -y
apt-get install -y curl git build-essential ufw fail2ban nginx certbot python3-certbot-nginx

echo -e "\n${GREEN}[2/6] 🟢 Installing Node.js 20.x LTS & PM2...${NC}"
if ! command -v node &> /dev/null; then
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
    apt-get install -y nodejs
fi
echo -e "${BLUE}Node.js Version: $(node -v)${NC}"
echo -e "${BLUE}NPM Version: $(npm -v)${NC}"

npm install -g pm2

echo -e "\n${GREEN}[3/6] 🛠️ Setting up LocNova Server & Dependencies...${NC}"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SERVER_DIR="$SCRIPT_DIR/server"

if [ ! -d "$SERVER_DIR" ]; then
    echo -e "${RED}❌ Error: 'server' directory not found in $SCRIPT_DIR${NC}"
    exit 1
fi

cd "$SERVER_DIR"
npm install --production

# Generate random JWT secret if .env doesn't exist
if [ ! -f "$SERVER_DIR/.env" ]; then
    JWT_SECRET=$(openssl rand -hex 32)
    cat <<EOF > "$SERVER_DIR/.env"
PORT=3000
JWT_SECRET=$JWT_SECRET
ADMIN_PASSWORD=admin123
DB_TYPE=sqlite
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=
DB_NAME=vps_license
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=
SMTP_PASS=
SMTP_FROM=
EOF
    echo -e "${YELLOW}⚠️ Created initial .env file. Default Database: SQLite (Change DB_TYPE=mysql for MySQL).${NC}"
fi

echo -e "\n${GREEN}[4/6] ⚡ Starting Application with PM2...${NC}"
pm2 stop locationsofi-vps 2>/dev/null || true
pm2 delete locationsofi-vps 2>/dev/null || true
pm2 start index.js --name "locationsofi-vps"
pm2 save
pm2 startup systemd -u root --hp /root || true

echo -e "\n${GREEN}[5/6] 🌐 Configuring Nginx Reverse Proxy & WebSockets...${NC}"
NGINX_CONF="/etc/nginx/sites-available/locationsofi"

cat <<EOF > "$NGINX_CONF"
server {
    listen 80;
    server_name $DOMAIN_OR_IP;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 86400;
    }
}
EOF

ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/default
nginx -t
systemctl restart nginx

# Setup Let's Encrypt SSL if domain was provided
if [ "$IS_IP" = false ]; then
    echo -e "\n${GREEN}🔒 Setting up Free SSL Certificate via Let's Encrypt (Certbot)...${NC}"
    certbot --nginx -d "$DOMAIN_OR_IP" --non-interactive --agree-tos -m "$ADMIN_EMAIL" || {
        echo -e "${YELLOW}⚠️ SSL Setup failed. Ensure DNS A record for $DOMAIN_OR_IP points to VPS IP.${NC}"
    }
fi

echo -e "\n${GREEN}[6/6] 🛡️ Hardening Firewall (UFW)...${NC}"
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo -e "\n${BLUE}====================================================${NC}"
echo -e "${GREEN}  🎉 LocNova VPS Installation Completed Successfully! ${NC}"
echo -e "${BLUE}====================================================${NC}"
if [ "$IS_IP" = false ]; then
    echo -e "🔗 Web Admin Panel: ${GREEN}https://$DOMAIN_OR_IP/admin/${NC}"
    echo -e "📚 Swagger API Docs: ${GREEN}https://$DOMAIN_OR_IP/api-docs/${NC}"
    echo -e "⚡ WebSocket Link:   ${GREEN}wss://$DOMAIN_OR_IP/ws${NC}"
else
    echo -e "🔗 Web Admin Panel: ${GREEN}http://$DOMAIN_OR_IP/admin/${NC}"
    echo -e "📚 Swagger API Docs: ${GREEN}http://$DOMAIN_OR_IP/api-docs/${NC}"
    echo -e "⚡ WebSocket Link:   ${GREEN}ws://$DOMAIN_OR_IP/ws${NC}"
fi
echo -e "🔑 Default Admin Login: ${YELLOW}admin${NC} / Password: ${YELLOW}admin123${NC}"
echo -e "📊 PM2 Status Command: ${BLUE}pm2 status${NC}"
echo -e "📜 Logs Command:       ${BLUE}pm2 logs locationsofi-vps${NC}"
echo -e "${BLUE}====================================================${NC}"
