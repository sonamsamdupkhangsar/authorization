CREATE TABLE user_entities
(
    id           varchar(1000) not null,
    name         varchar(1000) not null,
    display_name varchar(1000) not null,
    primary key (id)
);

CREATE UNIQUE INDEX user_entities_name_idx ON user_entities (name);

CREATE TABLE user_credentials
(
    credential_id                varchar(1000) not null,
    user_entity_user_id          varchar(1000) not null,
    public_key                   bytea         not null,
    signature_count              bigint,
    uv_initialized               boolean,
    backup_eligible              boolean       not null,
    authenticator_transports     varchar(1000),
    public_key_credential_type   varchar(100),
    backup_state                 boolean       not null,
    attestation_object           bytea,
    attestation_client_data_json bytea,
    created                      timestamp,
    last_used                    timestamp,
    label                        varchar(1000) not null,
    primary key (credential_id)
);

CREATE INDEX user_credentials_user_entity_user_id_idx ON user_credentials (user_entity_user_id);
