# ═══════════════════════════════════════════════════════════════════════════════
#  Lesyria - raccourcis de developpement et d'exploitation
#
#  Le profil compose est pilote par PROFILE (dev par defaut) :
#
#      make up                  # environnement de developpement
#      make up PROFILE=prod     # environnement de production
#
#  Maven n'est pas necessaire sur le poste : s'il est absent, les cibles de
#  build utilisent l'image officielle Maven dans Docker (cache local .m2/).
# ═══════════════════════════════════════════════════════════════════════════════

SHELL := /bin/bash

PROFILE   ?= dev
MVN_IMAGE ?= maven:3.9.16-eclipse-temurin-25
COMPOSE   ?= docker compose

# Le profil est force explicitement : il a ainsi toujours la priorite sur la
# valeur eventuelle de COMPOSE_PROFILES dans .env.
COMPOSE_RUN := COMPOSE_PROFILES=$(PROFILE) $(COMPOSE)

# Serveur et ports selon le profil
ifeq ($(PROFILE),prod)
  SERVICE    := minecraft
  RCON_PORT  := $(or $(RCON_PORT),25575)
  API_PORT   := $(or $(API_PORT),8080)
  PG_SERVICE := postgres
else
  SERVICE    := minecraft-dev
  RCON_PORT  := $(or $(RCON_DEV_PORT),25576)
  API_PORT   := $(or $(API_DEV_PORT),8081)
  PG_SERVICE := postgres-dev
endif

ifneq (, $(shell which mvn 2>/dev/null))
  MVN := mvn -B
else
  MVN := docker run --rm --user "$(shell id -u):$(shell id -g)" \
         -e MAVEN_CONFIG=/w/.m2 \
         -v "$(CURDIR)":/w -w /w $(MVN_IMAGE) mvn -B -Dmaven.repo.local=/w/.m2/repository
endif

.DEFAULT_GOAL := help
.PHONY: help build test verify clean up down restart ps logs plugin-logs db rcon psql shell api up-prod down-prod

help: ## Affiche cette aide
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "  Profil courant : PROFILE=$(PROFILE) (service $(SERVICE))"
	@echo "  Ports          : Java, API $(API_PORT), RCON $(RCON_PORT)"

# ─── Build du plugin ─────────────────────────────────────────────────────────
build: ## Compile et empaquette le plugin (target/lesyria.jar)
	$(MVN) clean package -DskipTests

test: ## Execute les tests unitaires
	$(MVN) test

verify: ## Chaine complete : compilation, tests, empaquetage
	$(MVN) clean verify

clean: ## Supprime les artefacts de build
	$(MVN) clean

# ─── Cycle de vie de la stack ────────────────────────────────────────────────
up: ## Demarre la stack du profil courant (PROFILE=dev|prod)
	$(COMPOSE_RUN) up -d --build
	@echo "Profil $(PROFILE) demarre. Journaux : make logs PROFILE=$(PROFILE)"

down: ## Arrete la stack du profil courant (les volumes sont conserves)
	$(COMPOSE_RUN) down

restart: ## Recree la stack du profil courant
	$(COMPOSE_RUN) up -d --build --force-recreate

ps: ## Etat des conteneurs du profil courant
	$(COMPOSE_RUN) ps

logs: ## Journaux du serveur Minecraft (profil courant)
	$(COMPOSE_RUN) logs -f $(SERVICE)

plugin-logs: ## Journaux du plugin Lesyria uniquement
	$(COMPOSE_RUN) logs -f $(SERVICE) | grep --line-buffered -E "Lesyria|ERROR|WARN"

db: ## Journaux de PostgreSQL (profil courant)
	$(COMPOSE_RUN) logs -f $(PG_SERVICE)

# ─── Outils ──────────────────────────────────────────────────────────────────
rcon: ## Commande au serveur : make rcon CMD="list"
	@test -n "$(CMD)" || { echo "Usage: make rcon CMD=\"list\""; exit 1; }
	@docker run --rm -i --network lesyria_lesyria \
	  -v "$(CURDIR)/scripts":/scripts:ro python:3-alpine \
	  python /scripts/rcon.py --host $(SERVICE) --port 25575 \
	  --password "$${RCON_PASSWORD:-lesyria-dev-rcon}" --command "$(CMD)"

psql: ## Ouvre psql sur la base du profil courant
	$(COMPOSE_RUN) exec $(PG_SERVICE) psql -U "$${POSTGRES_USER:-lesyria}" -d "$${POSTGRES_DB:-lesyria}"

shell: ## Shell dans le conteneur Minecraft
	$(COMPOSE_RUN) exec $(SERVICE) bash

api: ## Interroge l'API HTTP du profil courant
	@curl -fsS "http://127.0.0.1:$(API_PORT)/status" && echo

# ─── Raccourcis production ───────────────────────────────────────────────────
up-prod: ## Demarre la production (equivalent a make up PROFILE=prod)
	@$(MAKE) --no-print-directory up PROFILE=prod

down-prod: ## Arrete la production
	@$(MAKE) --no-print-directory down PROFILE=prod
