-- Eventos: agenda pública de eventos culturales, turísticos y deportivos
-- publicados por el municipio, alta protegida por el municipio, con
-- cancelación como único cambio de estado posible (R26, ADR 0030).
--
-- Sin columna de tenant: vive en la base del municipio, igual que
-- obra_publica (V19), arbol_urbano (V20) y espacio_verde (V25). A
-- diferencia de esas tres tablas, evento suma un rango de fechas propio
-- (fecha_inicio obligatoria, fecha_fin opcional) y una hora de
-- conveniencia (hora_inicio), sin hora_fin porque no es una franja
-- reservable (ADR 0030 §2).
create table evento (
    id                        bigint generated always as identity primary key,
    nombre                    varchar(200)   not null,
    categoria                 varchar(20)    not null
        check (categoria in ('CULTURA', 'TURISMO', 'DEPORTE', 'OTRA')),
    -- Texto libre, sin geolocalización estructurada ni GIS (ADR 0030 §8).
    ubicacion                 varchar(300)   not null,
    descripcion               text,

    fecha_inicio              date           not null,
    fecha_fin                 date,
    hora_inicio                time,

    -- Único salto sin retorno (ADR 0030 §3): sin estado intermedio, a
    -- diferencia de espacio_verde/obra_publica/arbol_urbano.
    estado                    varchar(20)    not null default 'PROGRAMADO'
        check (estado in ('PROGRAMADO', 'CANCELADO')),

    -- Copia del actor que publica el evento, no una relación con usuario:
    -- mismo criterio que publicado_por_nombre/email en obra_publica (V19,
    -- ADR 0013).
    publicado_por_nombre      varchar(150)   not null,
    publicado_por_email       varchar(200)   not null,

    creado_en                 timestamptz    not null default now(),
    actualizado_en            timestamptz    not null default now()
    -- Sin motivo de cancelación, recurrencia, adjuntos ni geolocalización
    -- estructurada (ADR 0030 §8, Pendiente de definir): fuera de alcance a
    -- propósito.
);

-- Orden y filtro principal de la agenda pública: por fecha del evento, no
-- por fecha de carga (ADR 0030 §4) — a diferencia de
-- espacio_verde_creado_en_idx/obra_publica_creado_en_idx.
create index evento_fecha_inicio_idx on evento (fecha_inicio);

-- Filtro más usado del portal público.
create index evento_estado_idx on evento (estado);

comment on table evento is
    'Eventos de la agenda cultural, turística y deportiva publicados por este municipio, con cancelación como único cambio de estado posible (R26, ADR 0030).';

-- Catálogo de permisos: área "Cultura, Turismo y Deportes". Un único
-- permiso cubre alta y cancelación: publicar un evento y cancelarlo es la
-- misma clase de tarea operativa, sin diferencia real de sensibilidad
-- (ADR 0030 §6). Se asigna a administrador y agente, mismo criterio que
-- espaciosverdes.gestionar (V25).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('eventos.gestionar', 'Cultura, Turismo y Deportes', 'eventos', 'gestionar',
     'Publicar un evento en la agenda cultural, turística o deportiva y cancelarlo.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'eventos.gestionar';
