# ═══════════════════════════════════════════════════════════════════════════════
#  Lesyria - image serveur (Paper + plugin Lesyria)
#
#  Deux etapes :
#    1. builder : compilation du plugin Maven (Java 25) ;
#    2. runtime : JRE 25 + Paper epingle + plugin.
#
#  Le serveur Paper n'est pas telecharge a l'execution : il est epingle par
#  version et verifie par empreinte SHA-256 au build, ce qui rend l'image
#  reproductible. Pour changer de version, mettez a jour PAPER_VERSION,
#  PAPER_BUILD et PAPER_SHA256 (ou les build args de docker compose).
# ═══════════════════════════════════════════════════════════════════════════════

# ─── Etape 1 : compilation du plugin ──────────────────────────────────────────
FROM maven:3.9.16-eclipse-temurin-25 AS plugin-builder

WORKDIR /build

# Cache des dependances Maven
COPY pom.xml ./
RUN mvn -B -q -Dmaven.repo.local=/build/.m2 dependency:go-offline || true

COPY src ./src

# Les tests d'integration necessitent Docker : ils sont joues par la CI,
# pas pendant la construction de l'image.
RUN mvn -B -Dmaven.repo.local=/build/.m2 clean package -DskipTests

# ─── Etape 2 : runtime Paper ──────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble

ARG PAPER_VERSION=26.2
ARG PAPER_BUILD=123
ARG PAPER_SHA256=7b7b3b43c009103e1971a0576c26f655a7dd9b56a0a2a4438e352c03a7fecd08

ENV PAPER_VERSION=${PAPER_VERSION} \
    PAPER_BUILD=${PAPER_BUILD}

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        procps \
        tzdata \
 && rm -rf /var/lib/apt/lists/*

# Telechargement verifie du serveur Paper
RUN mkdir -p /opt/paper \
 && curl -fsSL -o /opt/paper/paper.jar \
      "https://fill-data.papermc.io/v1/objects/${PAPER_SHA256}/paper-${PAPER_VERSION}-${PAPER_BUILD}.jar" \
 && echo "${PAPER_SHA256}  /opt/paper/paper.jar" | sha256sum -c -

# Plugin compile + script de demarrage
COPY --from=plugin-builder /build/target/lesyria.jar /opt/lesyria/lesyria.jar
COPY entrypoint.sh /opt/lesyria/entrypoint.sh
RUN chmod +x /opt/lesyria/entrypoint.sh

# Repertoire de travail persistant : monde, plugins, logs, configurations
RUN mkdir -p /server/plugins
WORKDIR /server
VOLUME ["/server"]

# Port Java, port Bedrock (reserve a Geyser, non active en beta),
# API HTTP publique, RCON
EXPOSE 25565/tcp
EXPOSE 25565/udp
EXPOSE 8080/tcp
EXPOSE 25575/tcp

HEALTHCHECK --start-period=300s --interval=30s --timeout=5s --retries=5 \
    CMD curl -fsS "http://127.0.0.1:${API_PORT:-8080}/status" >/dev/null || exit 1

ENTRYPOINT ["/opt/lesyria/entrypoint.sh"]
