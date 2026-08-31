-- Espacios Verdes: padrón público de plazas, parques y paseos
-- registrados por el municipio, alta protegida por el municipio, con
-- estado propio actualizable (R25, ADR 0029).
--
-- Sin columna de tenant: vive en la base del municipio, igual que
-- obra_publica (V19) y arbol_urbano (V20). A diferencia de arbol_urbano,
-- tipo es un enum cerrado (ADR 0029 §3), mismo criterio que
-- institucion_educativa (V24). superficie es la primera columna numérica
-- de magnitud del patrón (ADR 0029 §4).
create table espacio_verde (
    id                        bigint generated always as identity primary key,
    nombre                    varchar(150)   not null,
    tipo                      varchar(20)    not null
        check (tipo in ('PLAZA', 'PARQUE', 'PASEO', 'OTRA')),
    -- Texto libre, sin geolocalización estructurada ni GIS (ADR 0029 §6).
    ubicacion                 varchar(300)   not null,
    descripcion               text,
    -- Metros cuadrados, única columna numérica de magnitud del alta (ADR 0029 §4).
    superficie                numeric(10,2)
        check (superficie is null or superficie > 0),
    estado                    varchar(25)    not null default 'DISPONIBLE'
        check (estado in ('DISPONIBLE', 'EN_MANTENIMIENTO', 'CERRADO')),

    -- Copia del actor que registra el espacio verde, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en
    -- obra_publica (V19, ADR 0013).
    publicado_por_nombre      varchar(150)   not null,
    publicado_por_email       varchar(200)   not null,

    creado_en                 timestamptz    not null default now(),
    actualizado_en            timestamptz    not null default now()
    -- Sin motivo de cierre, inventario de equipamiento ni adjuntos (ADR
    -- 0029 §6, Pendiente de definir): fuera de alcance a propósito.
);

-- Orden del listado (ADR 0029 §2), mismo criterio que obra_publica_creado_en_idx.
create index espacio_verde_creado_en_idx on espacio_verde (creado_en desc);

-- Filtro más usado del portal público.
create index espacio_verde_estado_idx on espacio_verde (estado);

comment on table espacio_verde is
    'Espacios verdes (plazas, parques, paseos) registrados por este municipio, con estado propio actualizable (R25, ADR 0029).';

-- Catálogo de permisos: área "Ambiente y Servicios Públicos". Un único
-- permiso cubre alta y actualización de estado: registrar un espacio
-- verde y actualizar su estado son la misma clase de trabajo operativo de
-- mantenimiento de espacios públicos (ADR 0029 §7). Se asigna a
-- administrador y agente, mismo criterio que arbolado.gestionar (V20).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('espaciosverdes.gestionar', 'Ambiente y Servicios Públicos', 'espaciosverdes', 'gestionar',
     'Registrar un espacio verde y actualizar su estado.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'espaciosverdes.gestionar';
