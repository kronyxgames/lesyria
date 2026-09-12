#!/usr/bin/env bash
#
# Lesyria - entrypoint du conteneur Minecraft.
#
# Le service PostgreSQL utilise l'entrypoint officiel de son image ; ce script
# ne gere que le serveur Minecraft, qu'il prepare puis demarre :
#
#   1. validation de l'EULA ;
#   2. attente de la disponibilite de PostgreSQL ;
#   3. generation / mise a jour de server.properties depuis l'environnement ;
#   4. installation du plugin fourni par l'image ;
#   5. demarrage de Paper avec la memoire demandee, en laissant Docker
#      envoyer les signaux au JVM (arret propre des mondes).
#
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-/server}"
PLUGIN_SOURCE="${PLUGIN_SOURCE:-/opt/lesyria/lesyria.jar}"
PAPER_JAR="${PAPER_JAR:-/opt/paper/paper.jar}"
PROPERTIES="${SERVER_DIR}/server.properties"
DB_WAIT_SECONDS="${DB_WAIT_SECONDS:-90}"

log() { printf '%s [lesyria] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
fatal() { log "ERREUR: $*"; exit 1; }

# ─── 1. EULA ─────────────────────────────────────────────────────────────────
if [ "$(printf '%s' "${EULA:-false}" | tr '[:upper:]' '[:lower:]')" != "true" ]; then
  fatal "vous devez accepter la licence Minecraft : EULA=true (https://aka.ms/MinecraftEULA)"
fi

mkdir -p "${SERVER_DIR}/plugins" "${SERVER_DIR}/logs"
printf 'eula=true\n' > "${SERVER_DIR}/eula.txt"

# ─── 2. Disponibilite de PostgreSQL ──────────────────────────────────────────
# `depends_on: service_healthy` couvre le cas de Docker Compose ; cette attente
# protege aussi les lancements via `docker run`, ou le plugin ne doit pas
# s'initialiser contre une base absente.
resolve_database() {
  if [ -n "${DATABASE_URL:-}" ]; then
    local hostport="${DATABASE_URL#*@}"
    hostport="${hostport%%/*}"
    DB_HOST="${hostport%%:*}"
    DB_PORT="${hostport##*:}"
  else
    DB_HOST="${LESYRIA_DB_HOST:-postgres}"
    DB_PORT="${LESYRIA_DB_PORT:-5432}"
  fi
  [ -n "${DB_HOST}" ] || DB_HOST="postgres"
  case "${DB_PORT}" in
    ''|*[!0-9]*) DB_PORT="5432" ;;
  esac
}

wait_for_database() {
  resolve_database
  log "attente de PostgreSQL sur ${DB_HOST}:${DB_PORT} (max ${DB_WAIT_SECONDS}s)"
  local waited=0
  while ! (exec 3<>"/dev/tcp/${DB_HOST}/${DB_PORT}") 2>/dev/null; do
    if [ "${waited}" -ge "${DB_WAIT_SECONDS}" ]; then
      fatal "PostgreSQL injoignable sur ${DB_HOST}:${DB_PORT}. Verifiez DATABASE_URL et l'etat du service postgres."
    fi
    sleep 2
    waited=$((waited + 2))
  done
  exec 3>&- 2>/dev/null || true
  log "PostgreSQL joignable"
}

wait_for_database

# ─── 3. server.properties ────────────────────────────────────────────────────
# Le fichier est cree s'il n'existe pas, puis seules les cles gerees par
# l'environnement sont mises a jour : les reglages manuels sont conserves.
escape() { printf '%s' "$1" | sed -e 's/[&|\\]/\\&/g'; }

set_property() {
  local key="$1" value; value="$(escape "$2")"
  if grep -q "^${key}=" "${PROPERTIES}"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "${PROPERTIES}"
  else
    printf '%s=%s\n' "${key}" "${value}" >> "${PROPERTIES}"
  fi
}

if [ ! -f "${PROPERTIES}" ]; then
  log "creation de ${PROPERTIES}"
  : > "${PROPERTIES}"
fi

set_property "server-port"            "${SERVER_PORT:-25565}"
set_property "motd"                   "${MOTD:-Lesyria - beta}"
set_property "max-players"            "${MAX_PLAYERS:-100}"
set_property "online-mode"            "${ONLINE_MODE:-true}"
set_property "level-name"             "${LEVEL_NAME:-world}"
set_property "level-type"             "${LEVEL_TYPE:-minecraft:normal}"
set_property "level-seed"             "${LEVEL_SEED:-}"
set_property "gamemode"               "${GAMEMODE:-survival}"
set_property "difficulty"             "${DIFFICULTY:-normal}"
set_property "pvp"                    "${VANILLA_PVP:-true}"
set_property "view-distance"          "${VIEW_DISTANCE:-8}"
set_property "simulation-distance"    "${SIMULATION_DISTANCE:-6}"
set_property "spawn-protection"       "${SPAWN_PROTECTION:-0}"
set_property "enable-command-block"   "${ENABLE_COMMAND_BLOCK:-true}"
set_property "enable-rcon"            "${ENABLE_RCON:-true}"
set_property "rcon.port"              "${RCON_PORT:-25575}"
set_property "rcon.password"          "${RCON_PASSWORD:-lesyria-dev-rcon}"
set_property "white-list"             "${WHITELIST:-false}"
set_property "enforce-secure-profile" "${ENFORCE_SECURE_PROFILE:-true}"

# ─── 4. Plugin ───────────────────────────────────────────────────────────────
install -m 644 "${PLUGIN_SOURCE}" "${SERVER_DIR}/plugins/lesyria.jar"
log "plugin installe ($(stat -c%s "${SERVER_DIR}/plugins/lesyria.jar") octets)"

# ─── 5. Demarrage ────────────────────────────────────────────────────────────
MEMORY="${MEMORY:-2G}"
JAVA_FLAGS="${JAVA_FLAGS:--XX:+UseG1GC -XX:MaxGCPauseMillis=130 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=28 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:SurvivorRatio=32 -XX:MaxTenuringThreshold=1}"

log "Paper ${PAPER_VERSION:-?} build ${PAPER_BUILD:-?} - memoire ${MEMORY}"
log "demarrage du serveur (profil ${LESYRIA_PROFILE:-prod})"

cd "${SERVER_DIR}"
# exec : le JVM recoit directement SIGTERM, ce qui declenche l'arret propre
# du serveur (sauvegarde des mondes et arret du plugin).
exec java -Xms"${MEMORY}" -Xmx"${MEMORY}" ${JAVA_FLAGS} -jar "${PAPER_JAR}" --nogui "$@"
