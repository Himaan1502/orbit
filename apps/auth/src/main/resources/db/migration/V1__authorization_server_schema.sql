-- V1: Spring Authorization Server JDBC schema (clients, authorizations, consent).
--
-- Adapted for PostgreSQL from Spring Authorization Server 1.2.4's bundled schema
-- resources (org/springframework/security/oauth2/server/authorization/*.sql):
--   * every `blob` column becomes `text` (per the upstream file's own header
--     note — PostgreSQL has no `blob` type; JdbcOAuth2AuthorizationService
--     reads/writes these as strings);
--   * `varchar(N)` columns that carry serialised token metadata are widened to
--     `text` to remove any truncation risk.
--
-- These tables live in the `auth` schema (Flyway default-schema = auth). See
-- ADR-011. `apps/api` never touches this schema.

CREATE TABLE oauth2_registered_client (
    id                            VARCHAR(100)                            NOT NULL,
    client_id                     VARCHAR(100)                            NOT NULL,
    client_id_issued_at           TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret                 VARCHAR(200)  DEFAULT NULL,
    client_secret_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    client_name                   VARCHAR(200)                            NOT NULL,
    client_authentication_methods VARCHAR(1000)                           NOT NULL,
    authorization_grant_types     VARCHAR(1000)                           NOT NULL,
    redirect_uris                 VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
    scopes                        VARCHAR(1000)                           NOT NULL,
    client_settings               TEXT                                    NOT NULL,
    token_settings                TEXT                                    NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE TABLE oauth2_authorization (
    id                            VARCHAR(100) NOT NULL,
    registered_client_id          VARCHAR(100) NOT NULL,
    principal_name                VARCHAR(200) NOT NULL,
    authorization_grant_type      VARCHAR(100) NOT NULL,
    authorized_scopes             VARCHAR(1000) DEFAULT NULL,
    attributes                    TEXT          DEFAULT NULL,
    state                         VARCHAR(500)  DEFAULT NULL,
    authorization_code_value      TEXT          DEFAULT NULL,
    authorization_code_issued_at  TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_expires_at TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_metadata   TEXT          DEFAULT NULL,
    access_token_value            TEXT          DEFAULT NULL,
    access_token_issued_at        TIMESTAMPTZ   DEFAULT NULL,
    access_token_expires_at       TIMESTAMPTZ   DEFAULT NULL,
    access_token_metadata         TEXT          DEFAULT NULL,
    access_token_type             VARCHAR(100)  DEFAULT NULL,
    access_token_scopes           VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value           TEXT          DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_metadata        TEXT          DEFAULT NULL,
    refresh_token_value           TEXT          DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_metadata        TEXT          DEFAULT NULL,
    user_code_value               TEXT          DEFAULT NULL,
    user_code_issued_at           TIMESTAMPTZ   DEFAULT NULL,
    user_code_expires_at          TIMESTAMPTZ   DEFAULT NULL,
    user_code_metadata            TEXT          DEFAULT NULL,
    device_code_value             TEXT          DEFAULT NULL,
    device_code_issued_at         TIMESTAMPTZ   DEFAULT NULL,
    device_code_expires_at        TIMESTAMPTZ   DEFAULT NULL,
    device_code_metadata          TEXT          DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_oauth2_authorization_principal_name    ON oauth2_authorization (principal_name);
CREATE INDEX idx_oauth2_authorization_reg_client_id     ON oauth2_authorization (registered_client_id);
