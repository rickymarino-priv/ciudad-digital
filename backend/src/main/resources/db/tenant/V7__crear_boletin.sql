-- Boletín Oficial digital: ordenanzas, decretos, resoluciones y
-- comunicados publicados por el municipio, buscables por cualquiera
-- (backlog R7).
--
-- Sin columna de tenant: vive en la base del municipio, igual que
-- reclamo (V6) y registro_auditoria (V5).
create table norma (
    id                    bigint generated always as identity primary key,
    tipo                  varchar(20)  not null
        check (tipo in ('ORDENANZA', 'DECRETO', 'RESOLUCION', 'COMUNICADO')),
    -- Texto libre que asigna el municipio: la numeración oficial
    -- correlativa es un proceso legal fuera del alcance de esta rebanada,
    -- no se genera sola. No es único a propósito: dos tipos de norma
    -- distintos pueden compartir numeración según el criterio de cada
    -- municipio, y esta rebanada no arbitra esa convención.
    numero                varchar(50)  not null,
    titulo                varchar(300) not null,
    texto                 text         not null,
    -- Fecha que declara la norma, no necesariamente "ahora": puede
    -- cargarse en forma retroactiva. creado_en (abajo) es la que sí
    -- registra cuándo entró al sistema.
    fecha_publicacion     date         not null,
    -- Copia del actor al momento de publicar, no una relación con
    -- usuario: mismo criterio que actor_nombre/actor_email en
    -- registro_auditoria (V5, ADR 0013). Es la firma pública de la norma,
    -- no un dato que tenga que seguir vivo si ese usuario cambia de
    -- nombre o se desactiva después.
    publicado_por_nombre  varchar(150) not null,
    publicado_por_email   varchar(200) not null,
    creado_en             timestamptz  not null default now()
    -- Sin estado ni columnas de edición: una norma publicada no se edita
    -- ni se borra por esta rebanada. Es un registro público que se
    -- corrige publicando una norma nueva, no mutando la vieja —igual
    -- criterio que dejar fuera de alcance el versionado/derogación.
);

-- El listado público ordena por fecha de publicación descendente, sin
-- paginado (fuera de alcance de esta rebanada); mismo criterio que
-- reclamo_creado_en_idx (V6).
create index norma_fecha_publicacion_idx on norma (fecha_publicacion desc);

comment on table norma is
    'Normas publicadas en el Boletín Oficial de este municipio (backlog R7).';

-- Catálogo de permisos: área "Boletín Oficial". A diferencia de
-- reclamos.ver/reclamos.gestionar (V6, asignados a administrador y
-- agente porque es la operación diaria de atención al vecino),
-- boletin.publicar se asigna SOLO a administrador: publicar una norma es
-- un acto legal del municipio, de mayor confianza que gestionar un
-- reclamo, más cerca en sensibilidad de administrar usuarios/roles
-- (usuarios.administrar, roles.administrar, V2) que de operar el día a
-- día.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('boletin.publicar', 'Boletín Oficial', 'boletin', 'publicar',
     'Publicar una norma (ordenanza, decreto, resolución o comunicado) en el Boletín Oficial.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo = 'administrador'
  and p.codigo = 'boletin.publicar';
