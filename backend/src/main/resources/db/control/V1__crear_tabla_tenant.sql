-- Base de control: registro de municipios (tenants).
-- Modelo definido en ADR 0007: columnas explícitas para lo estructural,
-- columna JSON para lo variable (tema, módulos habilitados).

create table tenant (
    id                    uuid         primary key,
    slug                  varchar(50)  not null,
    nombre_municipio      varchar(200) not null,
    subdominio            varchar(63)  not null,
    dominio_personalizado varchar(253),
    estado                varchar(20)  not null,
    nombre_base_datos     varchar(63)  not null,
    fecha_alta            timestamptz  not null default now(),
    config                jsonb        not null default '{}'::jsonb,

    constraint tenant_slug_unico              unique (slug),
    constraint tenant_subdominio_unico        unique (subdominio),
    constraint tenant_dominio_unico           unique (dominio_personalizado),
    constraint tenant_base_datos_unica        unique (nombre_base_datos),
    constraint tenant_estado_valido check (
        estado in ('PENDIENTE', 'APROVISIONANDO', 'ACTIVO', 'SUSPENDIDO', 'ERROR')
    )
);

comment on table tenant is
    'Municipios dados de alta. Una fila por tenant; sus datos operativos viven en su propia base.';
comment on column tenant.nombre_base_datos is
    'Nombre de la base del tenant. Las credenciales son compartidas a nivel aplicación (ADR 0007).';
comment on column tenant.dominio_personalizado is
    'Dominio propio del municipio. Nulo mientras use solo el subdominio (ADR 0004).';
