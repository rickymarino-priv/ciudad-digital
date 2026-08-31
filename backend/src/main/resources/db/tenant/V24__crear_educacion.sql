-- Educación municipal: padrón público de instituciones educativas de
-- gestión municipal, alta protegida por el municipio, con estado propio
-- actualizable (R24, ADR 0028).
--
-- Sin columna de tenant: vive en la base del municipio, igual que
-- obra_publica (V19) y arbol_urbano (V20). A diferencia de ambas, esta
-- tabla no tiene ninguna columna de fecha propia.
create table institucion_educativa (
    id                        bigint generated always as identity primary key,
    nombre                    varchar(200)   not null,
    tipo                      varchar(35)    not null
        check (tipo in ('JARDIN_MATERNAL', 'JARDIN_DE_INFANTES', 'CENTRO_DE_FORMACION_PROFESIONAL', 'OTRA')),
    -- Texto libre, sin geolocalización estructurada ni GIS (ADR 0028 §3/§6).
    ubicacion                 varchar(300)   not null,
    descripcion               text,
    estado                    varchar(25)    not null default 'ACTIVA'
        check (estado in ('ACTIVA', 'CERRADA_TEMPORALMENTE', 'CERRADA_DEFINITIVAMENTE')),

    -- Copia del actor que registra la institución, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en
    -- obra_publica (V19, ADR 0013).
    publicado_por_nombre      varchar(150)   not null,
    publicado_por_email       varchar(200)   not null,

    creado_en                 timestamptz    not null default now(),
    actualizado_en            timestamptz    not null default now()
    -- Sin cupos/vacantes, inscripción de personas ni adjuntos (ADR 0028
    -- §6, Pendiente de definir): fuera de alcance a propósito.
);

-- Orden del listado (ADR 0028 §2), mismo criterio que obra_publica_creado_en_idx.
create index institucion_educativa_creado_en_idx on institucion_educativa (creado_en desc);

-- Filtro más usado del portal público.
create index institucion_educativa_estado_idx on institucion_educativa (estado);

comment on table institucion_educativa is
    'Instituciones educativas municipales registradas por este municipio, con estado propio actualizable (R24, ADR 0028).';

-- Catálogo de permisos: área "Educación municipal". Un único permiso cubre
-- alta y actualización de estado: registrar una institución y actualizar
-- su estado son la misma clase de trabajo operativo de gabinete (ADR 0028
-- §5). Se asigna a administrador y agente, mismo criterio que
-- obras.gestionar (V19)/arbolado.gestionar (V20).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('educacion.gestionar', 'Educación municipal', 'educacion', 'gestionar',
     'Registrar una institución educativa municipal y actualizar su estado.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'educacion.gestionar';
