-- Tasas municipales y su pago online (backlog R13, ADR 0018). Sin padrón
-- de contribuyentes real todavía: el "número de cuenta" es un
-- identificador simple que el municipio siembra al dar de alta cada
-- tasa, igual de informal que "numero" en norma (V7) o "numero_partida"
-- en partida_presupuestaria (V11).
create table tasa (
    id                        bigint generated always as identity primary key,
    numero_cuenta             varchar(50)   not null,
    concepto                  varchar(200)  not null,
    -- Texto libre (ej. "2026-08", "3er trimestre 2026"): la periodicidad
    -- real de cada tributo varía por municipio y por tasa, no se modela
    -- todavía como una entidad de calendario propia.
    periodo                   varchar(50)   not null,
    monto                     numeric(12,2) not null check (monto > 0),
    estado                    varchar(20)   not null default 'PENDIENTE'
        check (estado in ('PENDIENTE', 'PAGADA')),
    fecha_pago                timestamptz,
    -- Referencia que la pasarela (real o simulada) asigna al intento de
    -- pago en curso. Nula mientras no se inició ningún pago; se limpia
    -- si un pago se rechaza, para permitir reintentar (ver GestionDeTasas).
    referencia_externa_pago   varchar(100),
    -- Copia del actor al momento de publicar, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en norma
    -- (V7) y partida_presupuestaria (V11, ADR 0013).
    publicado_por_nombre      varchar(150)  not null,
    publicado_por_email       varchar(200)  not null,
    creado_en                 timestamptz   not null default now()
);

create index tasa_numero_cuenta_idx on tasa (numero_cuenta);

-- Única mientras no sea null: dos tasas distintas no pueden compartir un
-- intento de pago en curso.
create unique index tasa_referencia_externa_pago_idx on tasa (referencia_externa_pago)
    where referencia_externa_pago is not null;

comment on table tasa is
    'Tasas municipales sembradas por el municipio y su estado de pago online (backlog R13).';

-- Catálogo de permisos: área "Tasas". Publicar una tasa es un acto fiscal
-- del municipio (crea una deuda exigible), mismo nivel de sensibilidad
-- que boletin.publicar (V7) y transparencia.publicar (V11) — se asigna
-- SOLO a administrador.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('tasas.publicar', 'Tasas', 'tasas', 'publicar',
     'Dar de alta una tasa municipal para un número de cuenta.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo = 'administrador'
  and p.codigo = 'tasas.publicar';
