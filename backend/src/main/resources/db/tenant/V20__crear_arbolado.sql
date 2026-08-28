-- Arbolado Urbano: padrón público de árboles registrados por el
-- municipio, alta protegida por el municipio, con estado sanitario
-- propio actualizable (R20, ADR 0024).
--
-- Sin columna de tenant: vive en la base del municipio, igual que
-- obra_publica (V19). A diferencia de obra_publica, esta tabla no tiene
-- un enum de tipo (ADR 0024 §3): especie y ubicacion son texto libre.
create table arbol_urbano (
    id                        bigint generated always as identity primary key,
    -- Texto libre, sin catálogo fijo de especies (ADR 0024 §3).
    especie                   varchar(150)   not null,
    -- Texto libre, sin geolocalización estructurada ni GIS (ADR 0024 §6).
    ubicacion                 varchar(300)   not null,
    descripcion               text,
    estado                    varchar(25)    not null default 'PLANTADO'
        check (estado in ('PLANTADO', 'SANO', 'REQUIERE_INTERVENCION', 'RETIRADO')),
    fecha_de_plantacion       date,

    -- Copia del actor que registra el árbol, no una relación con usuario:
    -- mismo criterio que publicado_por_nombre/email en obra_publica (V19, ADR 0013).
    publicado_por_nombre      varchar(150)   not null,
    publicado_por_email       varchar(200)   not null,

    creado_en                 timestamptz    not null default now(),
    actualizado_en            timestamptz    not null default now()
    -- Sin motivo de retiro/intervención ni adjuntos (ADR 0024 §6, Pendiente
    -- de definir): fuera de alcance a propósito.
);

-- Orden del listado (ADR 0024 §2), mismo criterio que obra_publica_creado_en_idx.
create index arbol_urbano_creado_en_idx on arbol_urbano (creado_en desc);

-- Filtro más usado del portal público.
create index arbol_urbano_estado_idx on arbol_urbano (estado);

comment on table arbol_urbano is
    'Árboles urbanos registrados por este municipio, con estado sanitario propio actualizable (R20, ADR 0024).';

-- Catálogo de permisos: área "Ambiente y Servicios Públicos". Un único
-- permiso cubre alta y actualización de estado: registrar un árbol y
-- actualizar su estado sanitario son la misma clase de trabajo operativo
-- de campo (ADR 0024 §5). Se asigna a administrador y agente, mismo
-- criterio que obras.gestionar (V19).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('arbolado.gestionar', 'Ambiente y Servicios Públicos', 'arbolado', 'gestionar',
     'Registrar un árbol urbano y actualizar su estado sanitario.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'arbolado.gestionar';
