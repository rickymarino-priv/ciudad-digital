-- Contrato mínimo por municipio (ADR 0019): tramo poblacional (determina
-- el canon, cuyo monto vive fuera del sistema) y estado de facturación
-- (visibilidad manual, desacoplado del entitlement — ADR 0009). Todos con
-- default para no romper los municipios ya dados de alta.
alter table tenant
    add column tramo_poblacional  varchar(20) not null default 'MEDIANO',
    add column estado_facturacion varchar(20) not null default 'AL_DIA',
    add column nota_facturacion   text;

alter table tenant
    add constraint tenant_tramo_poblacional_valido check (
        tramo_poblacional in ('CHICO', 'MEDIANO', 'GRANDE')
    ),
    add constraint tenant_estado_facturacion_valido check (
        estado_facturacion in ('AL_DIA', 'ATRASADO')
    );

comment on column tenant.tramo_poblacional is
    'Tramo de canon por tamaño de municipio (ADR 0019). El monto no se modela acá.';
comment on column tenant.estado_facturacion is
    'Visibilidad manual del estado de cuenta. No afecta el entitlement (ADR 0009).';
