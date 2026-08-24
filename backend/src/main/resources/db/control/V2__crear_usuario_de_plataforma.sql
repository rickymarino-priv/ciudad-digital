-- Usuarios de plataforma: quienes operan la API cross-tenant de
-- administración de municipios (ADR 0010). Reemplazan el token compartido
-- de R2 (`ciudad.admin.token`): un token no identifica a nadie, un usuario
-- sí.
--
-- Viven en la base de control porque la superficie que protegen —dar de
-- alta municipios— es por definición cross-tenant: no tiene sentido
-- guardarlos en la base de ningún municipio en particular.

create table usuario_plataforma (
    id            bigint generated always as identity primary key,
    nombre        varchar(150) not null,
    email         varchar(200) not null,
    hash_password varchar(100) not null,
    activo        boolean      not null default true,
    creado_en     timestamptz  not null default now(),
    ultimo_acceso timestamptz
);

create unique index usuario_plataforma_email_unico on usuario_plataforma (lower(email));

comment on table usuario_plataforma is
    'Personas que pueden operar la API de administración de municipios.';
