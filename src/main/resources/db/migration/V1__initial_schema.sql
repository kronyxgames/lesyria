-- ═══════════════════════════════════════════════════════════════════════════════
-- Lesyria - schema initial de la beta
--
-- Invariants portes par la base :
--   * un joueur appartient a AU PLUS UNE nation   -> index unique nation_members(uuid)
--   * les soldes et tresoreries ne sont jamais negatifs -> CHECK (balance >= 0)
--   * un nom de nation / ville / territoire est unique sans tenir compte de la casse
--   * toute operation economique est journalisee dans economy_transactions
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─── Joueurs ──────────────────────────────────────────────────────────────────
CREATE TABLE player_profiles (
    uuid                 UUID PRIMARY KEY,
    username             TEXT        NOT NULL,
    first_join           TIMESTAMPTZ NOT NULL,
    last_join            TIMESTAMPTZ NOT NULL,
    balance              BIGINT      NOT NULL DEFAULT 0 CHECK (balance >= 0),
    home_world           TEXT,
    home_x               DOUBLE PRECISION,
    home_y               DOUBLE PRECISION,
    home_z               DOUBLE PRECISION,
    home_yaw             REAL,
    home_pitch           REAL,
    nation_id            BIGINT,
    level                INTEGER     NOT NULL DEFAULT 1 CHECK (level >= 1),
    experience           BIGINT      NOT NULL DEFAULT 0 CHECK (experience >= 0),
    stat_kills           INTEGER     NOT NULL DEFAULT 0,
    stat_deaths          INTEGER     NOT NULL DEFAULT 0,
    stat_blocks_placed   BIGINT      NOT NULL DEFAULT 0,
    stat_blocks_broken   BIGINT      NOT NULL DEFAULT 0,
    stat_quests_completed INTEGER    NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX player_profiles_username_idx ON player_profiles (lower(username));
CREATE INDEX player_profiles_nation_idx ON player_profiles (nation_id);

-- ─── Nations ──────────────────────────────────────────────────────────────────
CREATE TABLE nations (
    id              BIGSERIAL   PRIMARY KEY,
    name            TEXT        NOT NULL,
    tag             TEXT        NOT NULL,
    leader_uuid     UUID        NOT NULL REFERENCES player_profiles (uuid),
    capital_city_id BIGINT,
    treasury        BIGINT      NOT NULL DEFAULT 0 CHECK (treasury >= 0),
    description     TEXT        NOT NULL DEFAULT '',
    open_join       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX nations_name_lower_key ON nations (lower(name));
CREATE UNIQUE INDEX nations_tag_lower_key ON nations (lower(tag));

CREATE TABLE nation_members (
    nation_id BIGINT      NOT NULL REFERENCES nations (id) ON DELETE CASCADE,
    uuid      UUID        NOT NULL REFERENCES player_profiles (uuid) ON DELETE CASCADE,
    role      TEXT        NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (nation_id, uuid)
);

-- Un joueur ne peut appartenir qu'a une seule nation a la fois
CREATE UNIQUE INDEX nation_members_single_nation_key ON nation_members (uuid);

CREATE TABLE nation_invites (
    nation_id  BIGINT      NOT NULL REFERENCES nations (id) ON DELETE CASCADE,
    uuid       UUID        NOT NULL REFERENCES player_profiles (uuid) ON DELETE CASCADE,
    invited_by UUID        REFERENCES player_profiles (uuid) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (nation_id, uuid)
);

-- ─── Territoires ──────────────────────────────────────────────────────────────
CREATE TABLE territories (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT      NOT NULL,
    nation_id   BIGINT    NOT NULL REFERENCES nations (id) ON DELETE CASCADE,
    owner_uuid  UUID      REFERENCES player_profiles (uuid) ON DELETE SET NULL,
    world       TEXT      NOT NULL,
    min_chunk_x INTEGER   NOT NULL,
    min_chunk_z INTEGER   NOT NULL,
    max_chunk_x INTEGER   NOT NULL,
    max_chunk_z INTEGER   NOT NULL,
    status      TEXT      NOT NULL DEFAULT 'ACTIVE',
    permissions JSONB     NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (min_chunk_x <= max_chunk_x),
    CHECK (min_chunk_z <= max_chunk_z)
);

CREATE UNIQUE INDEX territories_name_lower_key ON territories (lower(name));
CREATE INDEX territories_nation_idx ON territories (nation_id);
CREATE INDEX territories_world_chunks_idx ON territories (world, min_chunk_x, max_chunk_x);

-- ─── Villes ───────────────────────────────────────────────────────────────────
CREATE TABLE cities (
    id          BIGSERIAL PRIMARY KEY,
    nation_id   BIGINT    NOT NULL REFERENCES nations (id) ON DELETE CASCADE,
    territory_id BIGINT   REFERENCES territories (id) ON DELETE SET NULL,
    name        TEXT      NOT NULL,
    world       TEXT      NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL      NOT NULL DEFAULT 0,
    pitch       REAL      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX cities_name_lower_key ON cities (lower(name));
CREATE INDEX cities_nation_idx ON cities (nation_id);

-- ─── Contraintes differees (references croisees) ──────────────────────────────
ALTER TABLE player_profiles
    ADD CONSTRAINT player_profiles_nation_fk
    FOREIGN KEY (nation_id) REFERENCES nations (id) ON DELETE SET NULL;

ALTER TABLE nations
    ADD CONSTRAINT nations_capital_city_fk
    FOREIGN KEY (capital_city_id) REFERENCES cities (id) ON DELETE SET NULL;

-- ─── Journal economique ───────────────────────────────────────────────────────
CREATE TABLE economy_transactions (
    id                   BIGSERIAL   PRIMARY KEY,
    type                 TEXT        NOT NULL,
    source_kind          TEXT,
    source_id            TEXT,
    target_kind          TEXT,
    target_id            TEXT,
    amount               BIGINT      NOT NULL CHECK (amount > 0),
    reason               TEXT        NOT NULL DEFAULT '',
    source_balance_after BIGINT,
    target_balance_after BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX economy_transactions_created_idx ON economy_transactions (created_at DESC);
CREATE INDEX economy_transactions_source_idx ON economy_transactions (source_kind, source_id);
CREATE INDEX economy_transactions_target_idx ON economy_transactions (target_kind, target_id);
