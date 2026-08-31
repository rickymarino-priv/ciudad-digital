-- Prensa y Comunicación: gacetillas y comunicados de prensa publicados por
-- el municipio, buscables por cualquiera (R23, ADR 0027).
--
-- Sin columna de tenant: vive en la base del municipio, igual que norma
-- (V7) y registro_auditoria (V5).
create table gacetilla (
    id                    bigint generated always as identity primary key,
    -- Enum cerrado (ADR 0027 §1): alcanza para separar los temas más
    -- comunes de prensa municipal sin inventar un nomenclador más fino,
    -- mismo criterio que tipo en norma (V7)/actividad (V22).
    categoria             varchar(20)  not null
        check (categoria in ('INSTITUCIONAL', 'OBRAS', 'CULTURA', 'DEPORTES', 'SALUD', 'SEGURIDAD', 'OTRAS')),
    titulo                varchar(300) not null,
    texto                 text         not null,
    -- Fecha que declara la gacetilla, no necesariamente "ahora": puede
    -- cargarse en forma retroactiva, igual que fecha_publicacion en norma
    -- (V7). creado_en (abajo) es la que sí registra cuándo entró al
    -- sistema.
    fecha_publicacion     date         not null,
    -- Copia del actor al momento de publicar, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en norma
    -- (V7, ADR 0013). Es la firma pública de la gacetilla, no un dato que
    -- tenga que seguir vivo si ese usuario cambia de nombre o se
    -- desactiva después.
    publicado_por_nombre  varchar(150) not null,
    publicado_por_email   varchar(200) not null,
    creado_en             timestamptz  not null default now()
    -- Sin numero (a diferencia de norma, V7): una gacetilla no es un acto
    -- legal con numeración correlativa (ADR 0027 §1). Sin estado ni
    -- columnas de edición: una gacetilla publicada no se edita ni se
    -- borra por esta rebanada, mismo criterio que norma.
);

-- El listado público ordena por fecha de publicación descendente, sin
-- paginado (fuera de alcance de esta rebanada); mismo criterio que
-- norma_fecha_publicacion_idx (V7).
create index gacetilla_fecha_publicacion_idx on gacetilla (fecha_publicacion desc);

comment on table gacetilla is
    'Gacetillas de prensa publicadas por este municipio (R23, ADR 0027).';

-- Catálogo de permisos: área "Prensa y Comunicación". A diferencia de
-- boletin.publicar (V7, asignado solo a administrador porque publicar una
-- norma es un acto legal del municipio), prensa.publicar se asigna a
-- administrador Y agente: una gacetilla de prensa no es un acto legal,
-- es una comunicación operativa del mismo nivel de confianza que
-- gestionar un reclamo o dar de alta una franja de turnos — tareas que ya
-- delegan su permiso a ambos roles de sistema (ADR 0027 §3). No es una
-- inconsistencia con Boletín: es una diferencia de fondo entre los dos
-- dominios.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('prensa.publicar', 'Prensa y Comunicación', 'prensa', 'publicar',
     'Publicar una gacetilla de prensa del municipio.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo in ('administrador', 'agente')
  and p.codigo = 'prensa.publicar';
