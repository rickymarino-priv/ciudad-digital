-- Situación fiscal del proveedor según el padrón consultado en el alta
-- (backlog R16, ADR 0020): advisory, no bloquea ni el alta ni la
-- aprobación — es información para que el municipio decida, no una
-- condición que el sistema imponga. Se calcula una única vez, en el
-- alta (GestionDeProveedores.registrar), contra el único adaptador que
-- existe hoy (PadronFiscalSimulado, sin llamadas de red).
alter table proveedor
    add column situacion_fiscal varchar(20) not null default 'NO_ENCONTRADO'
        check (situacion_fiscal in ('ACTIVO', 'INHABILITADO', 'NO_ENCONTRADO'));

comment on column proveedor.situacion_fiscal is
    'Resultado (advisory, no bloqueante) de consultar el CUIT contra el padrón fiscal simulado en el alta (ADR 0020).';
