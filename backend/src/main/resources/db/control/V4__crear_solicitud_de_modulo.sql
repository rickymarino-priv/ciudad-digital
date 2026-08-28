-- Pedido de un municipio de alta o baja de un módulo (R18, ADR 0022). Vive
-- en la base de control, con tenant_id como FK, porque es dato contractual
-- (mismo criterio que tramo_poblacional/estado_facturacion en V3): así la
-- consola del proveedor puede listarlo sin leer nada operativo de la base
-- de un municipio (ADR 0019 §5).
create table solicitud_modulo (
    id                     bigint generated always as identity primary key,
    tenant_id              uuid          not null references tenant (id),
    modulo_codigo          varchar(60)   not null,
    tipo                   varchar(10)   not null check (tipo in ('ALTA', 'BAJA')),
    justificacion          varchar(1000) not null,
    estado                 varchar(20)   not null default 'PENDIENTE'
        check (estado in ('PENDIENTE', 'ATENDIDA')),
    solicitada_por_nombre  varchar(150)  not null,
    solicitada_por_email   varchar(200)  not null,
    creada_en              timestamptz   not null default now(),
    atendida_en            timestamptz
);

create index solicitud_modulo_tenant_idx on solicitud_modulo (tenant_id);

comment on table solicitud_modulo is
    'Pedido de un municipio de alta o baja de un módulo (R18, ADR 0022). '
    'No cambia el entitlement por sí sola: la plataforma sigue prendiendo '
    'o apagando módulos por separado (ADR 0012 §8).';
