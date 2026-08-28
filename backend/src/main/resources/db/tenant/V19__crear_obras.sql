-- Obras Públicas: registro público de obras en curso, alta protegida por
-- el municipio, con estado propio actualizable (R19, ADR 0023).
--
-- Sin columna de tenant: vive en la base del municipio, igual que norma
-- (V7) y reclamo. A diferencia de norma, esta tabla sí muta después de
-- creada, pero solo la columna estado (ADR 0023 §4) — el resto de los
-- datos del alta no se edita en esta rebanada.
create table obra_publica (
    id                        bigint generated always as identity primary key,
    nombre                    varchar(200)   not null,
    tipo                      varchar(20)    not null
        check (tipo in ('VIALIDAD', 'ESPACIO_PUBLICO', 'EDIFICIO_PUBLICO', 'SERVICIOS', 'OTRA')),
    -- Texto libre, sin geolocalización estructurada ni GIS (ADR 0023 §6).
    ubicacion                 varchar(300)   not null,
    descripcion               text,
    estado                    varchar(20)    not null default 'PLANIFICADA'
        check (estado in ('PLANIFICADA', 'EN_EJECUCION', 'PARALIZADA', 'FINALIZADA')),
    fecha_inicio_estimada     date,
    fecha_fin_estimada        date,

    -- Copia del actor que registra la obra, no una relación con usuario:
    -- mismo criterio que publicado_por_nombre/email en norma (V7, ADR 0013).
    publicado_por_nombre      varchar(150)   not null,
    publicado_por_email       varchar(200)   not null,

    creado_en                 timestamptz    not null default now(),
    actualizado_en            timestamptz    not null default now()
    -- Sin monto, contratista, certificaciones de avance ni adjuntos
    -- (ADR 0023 §7/§8): fuera de alcance a propósito.
);

-- Orden del listado (ADR 0023 §2), mismo criterio que norma_fecha_publicacion_idx.
create index obra_publica_creado_en_idx on obra_publica (creado_en desc);

-- Filtro más usado del portal público.
create index obra_publica_estado_idx on obra_publica (estado);

comment on table obra_publica is
    'Obras públicas en curso registradas por este municipio, con estado propio actualizable (R19, ADR 0023).';

-- Catálogo de permisos: área "Obras Públicas". Un único permiso cubre alta
-- y actualización de estado: no hay una acción con impacto fiscal ni
-- discrecional que amerite separarlas (ADR 0023 §5). Se asigna a
-- administrador y agente, mismo criterio que multas.labrar (V17).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('obras.gestionar', 'Obras Públicas', 'obras', 'gestionar',
     'Registrar una obra pública y actualizar su estado de avance.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'obras.gestionar';
