-- Transparencia activa básica: presupuesto (partidas y montos) y escala
-- salarial (cargos y montos, sin datos de personas) del municipio, con
-- consulta pública (backlog R11).
--
-- Sin columna de tenant: vive en la base del municipio, igual que norma
-- (V7) y sepultura (V8).
create table partida_presupuestaria (
    id                    bigint generated always as identity primary key,
    anio                  integer      not null
        check (anio between 2000 and 2100),
    area                  varchar(150) not null,
    -- Texto libre que asigna el municipio, mismo criterio que "numero" en
    -- norma (V7): la nomenclatura presupuestaria oficial (ej. RAFAM) es
    -- un problema de un municipio piloto real, no de esta rebanada.
    numero_partida        varchar(50)  not null,
    concepto              varchar(300) not null,
    monto_asignado        numeric(14,2) not null check (monto_asignado >= 0),
    -- Opcional: no todos los municipios llevan la ejecución al día.
    monto_ejecutado       numeric(14,2) check (monto_ejecutado is null or monto_ejecutado >= 0),
    -- Copia del actor al momento de publicar, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en norma
    -- (V7, ADR 0013). Es la firma pública del acto de publicar, no un
    -- dato de un tercero.
    publicado_por_nombre  varchar(150) not null,
    publicado_por_email   varchar(200) not null,
    creado_en             timestamptz  not null default now()
    -- Sin estado ni columnas de edición: un registro publicado no se
    -- edita ni se borra por esta rebanada, mismo criterio que norma.
);

create index partida_presupuestaria_anio_idx on partida_presupuestaria (anio desc, creado_en desc);

comment on table partida_presupuestaria is
    'Partidas presupuestarias publicadas por este municipio en Transparencia Activa (backlog R11).';

-- Escala salarial: cargo/función y monto, NUNCA una persona. A
-- diferencia de sepultura (V8), donde el dato privado se guarda y se
-- oculta en la búsqueda pública, acá el dato de persona directamente no
-- existe como columna: es una decisión de modelo, no de presentación
-- (ver la spec de R11 para el razonamiento completo).
create table escala_salarial (
    id                    bigint generated always as identity primary key,
    anio                  integer      not null
        check (anio between 2000 and 2100),
    area                  varchar(150) not null,
    cargo                 varchar(200) not null,
    cantidad_cargos       integer      not null default 1 check (cantidad_cargos > 0),
    monto_bruto_mensual   numeric(14,2) not null check (monto_bruto_mensual >= 0),
    publicado_por_nombre  varchar(150) not null,
    publicado_por_email   varchar(200) not null,
    creado_en             timestamptz  not null default now()
);

create index escala_salarial_anio_idx on escala_salarial (anio desc, creado_en desc);

comment on table escala_salarial is
    'Escala salarial por cargo/función publicada por este municipio en Transparencia Activa, sin datos de personas (backlog R11).';

-- Catálogo de permisos: área "Transparencia". Igual criterio que
-- boletin.publicar (V7): publicar presupuesto o escala salarial es un
-- acto de transparencia institucional del municipio, de mayor
-- sensibilidad que la operación diaria de reclamos/cementerio — se
-- asigna SOLO a administrador.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('transparencia.publicar', 'Transparencia', 'transparencia', 'publicar',
     'Publicar una partida presupuestaria o una entrada de escala salarial en Transparencia Activa.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo = 'administrador'
  and p.codigo = 'transparencia.publicar';
