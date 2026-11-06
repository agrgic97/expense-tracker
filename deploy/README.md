# Deployment auf den STRATO-VPS

Ziel: `https://deinearmutkotztmichan.com`. Auf dem Server laufen Caddy (HTTPS mit Let's Encrypt), Frontend, Backend und PostgreSQL in Docker.
Die Images werden auf dem Mac gebaut und per SSH übertragen, auf dem Server wird nichts kompiliert.

```
Internet ──443──> Caddy ──/api/*──> backend:8080 ──> db:5432
                        └──sonst───> frontend:80 (Vue)
```

Platzhalter unten: `SERVER_IP` = IPv4 des VPS (STRATO-Kundenlogin → Server), `DOMAIN` = `deinearmutkotztmichan.com`.

## 1. DNS bei STRATO setzen (zuerst, weil es dauern kann)

Im STRATO-Kundenlogin bei der Domain die DNS-Einstellungen öffnen:

| Typ | Name | Wert |
|---|---|---|
| A | `@` (die Domain selbst) | `SERVER_IP` |
| A | `www` | `SERVER_IP` |
| AAAA | `@` und `www` | IPv6 des VPS **oder löschen** |

Wichtig: Steht noch ein AAAA-Eintrag auf STRATOs Parkseite, scheitert das HTTPS-Zertifikat, weil Let's Encrypt IPv6 bevorzugt.
Prüfen (vom Mac): `dig +short DOMAIN A` und `dig +short DOMAIN AAAA` müssen die Adressen des VPS zeigen.

## 2. Server einrichten (einmalig, ca. 20 Minuten)

Betriebssystem im STRATO-Panel: **Ubuntu 24.04 LTS**. In der STRATO-Firewall (falls aktiv) die Ports **22, 80, 443** (TCP) und **443 (UDP)** freigeben.

Vom Mac aus den SSH-Schlüssel hinterlegen und einloggen (Root-Passwort aus dem STRATO-Panel):

```bash
ls ~/.ssh/id_ed25519.pub || ssh-keygen -t ed25519      # Schlüssel anlegen, falls keiner existiert
ssh-copy-id root@SERVER_IP
ssh root@SERVER_IP
```

Auf dem Server als `root`:

```bash
# System aktuell halten, automatische Sicherheitsupdates
apt update && apt -y full-upgrade
apt -y install unattended-upgrades ufw
dpkg-reconfigure -f noninteractive unattended-upgrades

# Benutzer für Deployments, mit deinem SSH-Schlüssel
adduser --disabled-password --gecos "" deploy
usermod -aG sudo deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
install -m 600 -o deploy -g deploy ~/.ssh/authorized_keys /home/deploy/.ssh/authorized_keys
passwd deploy                                           # Passwort für sudo setzen

# Docker (offizielles Installationsskript von Docker)
curl -fsSL https://get.docker.com | sh
usermod -aG docker deploy

# 2 GB Swap als Puffer für die 2 GB RAM
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# Firewall
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp
ufw --force enable

# App-Verzeichnis
install -d -o deploy -g deploy /opt/expense-tracker
```

**In einem zweiten Terminal** testen, dass `ssh deploy@SERVER_IP` und dort `sudo -v` funktionieren. Erst dann SSH absichern (die Datei beginnt mit `00-`, damit sie Ubuntus Cloud-Voreinstellungen überschreibt):

```bash
printf 'PermitRootLogin no\nPasswordAuthentication no\nKbdInteractiveAuthentication no\n' > /etc/ssh/sshd_config.d/00-hardening.conf
sshd -t && systemctl restart ssh
```

Ab jetzt nur noch als `deploy` einloggen. Hinweis: Docker-Ports umgehen `ufw`. Das ist hier gewollt, denn nur Caddy veröffentlicht Ports (80/443), die Datenbank ist von außen nicht erreichbar.

## 3. Konfiguration auf dem Server anlegen

```bash
ssh deploy@SERVER_IP
cd /opt/expense-tracker
cat > .env <<EOF
DOMAIN=deinearmutkotztmichan.com
POSTGRES_DB=expenses
POSTGRES_USER=expenses
POSTGRES_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
ACCESS_TOKEN_VALIDITY=15m
REFRESH_TOKEN_VALIDITY=30d
REGISTRATION_ENABLED=false
TZ=Europe/Berlin
EOF
chmod 600 .env
```

Die Datei enthält die Geheimnisse und verlässt den Server nie. Sie gehört nicht ins Git. Eine Vorlage liegt unter `deploy/.env.example`.

## 4. Deployen (vom Mac, im Projektordner)

```bash
./deploy/deploy.sh deploy@SERVER_IP
```

Das Skript baut die Images für `linux/amd64`, überträgt sie per SSH, kopiert `compose.yaml`, `Caddyfile` und `backup.sh` und startet den Stack.
Beim ersten Start holt Caddy automatisch das Zertifikat, sofern der DNS aus Schritt 1 schon auf den Server zeigt. Kontrolle: `ssh deploy@SERVER_IP 'cd /opt/expense-tracker && docker compose logs caddy'`.

Updates später: Code committen und dasselbe Kommando erneut ausführen.

## 5. Daten vom Mac übernehmen

Lokal läuft der Entwicklungs-Stack (`docker compose up -d` im Projektordner). Dein Konto und alle Ausgaben werden 1:1 übernommen, deshalb bleibt die Registrierung aus.

```bash
# Mac: Dump erstellen und hochladen
docker compose exec -T db pg_dump -U expenses -d expenses --clean --if-exists | gzip > /tmp/expenses-migration.sql.gz
scp /tmp/expenses-migration.sql.gz deploy@SERVER_IP:/opt/expense-tracker/

# Server: einspielen
ssh deploy@SERVER_IP
cd /opt/expense-tracker
docker compose stop backend
gunzip -c expenses-migration.sql.gz | docker compose exec -T db sh -c 'psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose start backend
rm expenses-migration.sql.gz
```

Danach `https://deinearmutkotztmichan.com` öffnen und mit deinem bisherigen Konto anmelden.

## 6. Backups

Tägliches Backup um 3:30 Uhr, die letzten 14 Tage bleiben auf dem Server liegen:

```bash
ssh deploy@SERVER_IP
(crontab -l 2>/dev/null; echo '30 3 * * * /opt/expense-tracker/backup.sh >> /opt/expense-tracker/backup.log 2>&1') | crontab -
/opt/expense-tracker/backup.sh            # einmal testen
```

Regelmäßig auf den Mac holen, denn ein Backup nur auf dem Server schützt nicht vor dessen Ausfall:

```bash
rsync -av deploy@SERVER_IP:/opt/expense-tracker/backups/ ~/Backups/expense-tracker/
```

Wiederherstellen: wie Schritt 5, nur mit einer Datei aus `backups/`.

## Nützliche Befehle (auf dem Server in `/opt/expense-tracker`)

| Zweck | Befehl |
|---|---|
| Status | `docker compose ps` |
| Logs | `docker compose logs -f backend` |
| Neustart | `docker compose restart` |
| Basis-Images aktualisieren (monatlich) | `docker compose pull caddy db && docker compose up -d` |
| Speicherverbrauch | `docker stats --no-stream` |
