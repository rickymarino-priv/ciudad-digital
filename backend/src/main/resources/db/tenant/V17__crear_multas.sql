-- Multas de tránsito del Juzgado de Faltas (R17, ADR 0021). A diferencia de
-- tasa (V14), el alta la origina el municipio, no el vecino: labrada_por_*
-- es la copia del agente que constata la infracción, no de quien publica
-- una obligación fiscal desde una oficina.
create table multa (
    id                        bigint generated always as identity primary key,
    patente                   varchar(20)    not null,
    -- Nullable: no toda infracción tiene el DNI del conductor identificado
    -- en el momento de labrar el acta, la patente alcanza (ADR 0021).
    dni                       varchar(20),
    descripcion_infraccion    varchar(500)   not null,
    monto_original            numeric(12,2)  not null check (monto_original > 0),
    estado                    varchar(20)    not null default 'NOTIFICADA'
        check (estado in ('NOTIFICADA', 'EN_DESCARGO', 'CONFIRMADA', 'ANULADA', 'PAGADA')),
    notificada_en             timestamptz    not null default now(),

    -- Copia del actor que labra, no una relación con usuario: mismo
    -- criterio que publicado_por_nombre/email en tasa (V14, ADR 0013).
    labrada_por_nombre        varchar(150)   not null,
    labrada_por_email         varchar(200)   not null,

    -- Descargo del vecino (ADR 0021 §5): un único ciclo por multa, sin
    -- tabla de movimientos separada.
    descargo_texto            varchar(2000),
    descargo_contacto         varchar(200),
    descargo_presentado_en    timestamptz,

    -- Resolución del descargo por el Juzgado de Faltas. resuelto_por_* es,
    -- otra vez, copia del actor, no una relación (ADR 0013/ADR 0021 §5).
    resolucion_comentario     varchar(2000),
    resuelto_por_nombre       varchar(150),
    resuelto_por_email        varchar(200),
    resuelto_en               timestamptz,

    -- Pago, mismo patrón que tasa (V14): referencia_externa_pago se limpia
    -- si un pago se rechaza, para permitir reintentar.
    fecha_pago                timestamptz,
    referencia_externa_pago   varchar(100)
);

create index multa_patente_idx on multa (patente);
create index multa_dni_idx on multa (dni);

-- Única mientras no sea null: dos multas distintas no pueden compartir un
-- intento de pago en curso (mismo criterio que tasa_referencia_externa_pago_idx, V14).
create unique index multa_referencia_externa_pago_idx on multa (referencia_externa_pago)
    where referencia_externa_pago is not null;

comment on table multa is
    'Actas de infracción de tránsito y su ciclo de vida propio (R17, ADR 0021).';

-- Catálogo de permisos: área "Multas". Labrar es trabajo operativo
-- cotidiano de un agente de tránsito, se asigna a administrador y agente
-- (ADR 0021 §3). Resolver un descargo tiene impacto fiscal y naturaleza
-- cuasi-judicial, se reserva solo a administrador (ADR 0021 §4).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('multas.labrar', 'Multas', 'multas', 'labrar',
     'Labrar un acta de infracción de tránsito.'),
    ('multas.resolverDescargo', 'Multas', 'multas', 'resolverDescargo',
     'Confirmar o anular una multa con descargo presentado.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'multas.labrar';

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo = 'administrador' and p.codigo = 'multas.resolverDescargo';
