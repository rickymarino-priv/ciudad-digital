-- Token de seguimiento anónimo (ADR 0017): el vecino que cargó un
-- reclamo sin sesión recibe, una única vez, un token en claro para
-- volver a consultar el estado más adelante. Acá solo se guarda su hash
-- SHA-256, nunca el token en claro.
alter table reclamo add column token_hash varchar(64) not null;

create unique index reclamo_token_hash_idx on reclamo (token_hash);

comment on column reclamo.token_hash is
    'Hash SHA-256 del token de seguimiento anónimo (ADR 0017). El token en claro no se guarda en ningún lado.';
