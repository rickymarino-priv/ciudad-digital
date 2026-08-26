-- Token de seguimiento anónimo (ADR 0017): mismo mecanismo que reclamo
-- (ver esa migración para el razonamiento completo).
alter table expediente add column token_hash varchar(64) not null;

create unique index expediente_token_hash_idx on expediente (token_hash);

comment on column expediente.token_hash is
    'Hash SHA-256 del token de seguimiento anónimo (ADR 0017). El token en claro no se guarda en ningún lado.';
