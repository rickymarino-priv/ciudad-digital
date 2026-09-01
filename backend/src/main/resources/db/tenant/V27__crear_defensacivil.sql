-- Defensa Civil: alertas públicas y recursos de emergencia (refugios,
-- puntos de encuentro, centros de acopio) publicados por el municipio,
-- ambos con alta protegida por el municipio (R27, ADR 0031).
--
-- Sin columna de tenant: viven en la base del municipio, igual que
-- obra_publica (V19), arbol_urbano (V20) y evento (V26). Sin clave foránea
-- entre las dos tablas: son dos catálogos independientes que comparten
-- pantalla por afinidad de dominio, no por relación de datos (ADR 0031
-- §1).
create table alerta_defensa_civil (
    id                        bigint generated always as identity primary key,
    tipo                      varchar(20)    not null
        check (tipo in ('METEOROLOGICA', 'INUNDACION', 'OLA_DE_CALOR', 'INCENDIO', 'OTRA')),
    -- Convención real del Servicio Meteorológico Nacional, no una escala
    -- inventada para este producto (ADR 0031 §4).
    nivel                     varchar(10)    not null
        check (nivel in ('AMARILLO', 'NARANJA', 'ROJO')),

    titulo                    varchar(300)   not null,
    descripcion               text           not null,
    recomendaciones           text           not null,
    -- Texto libre, sin geolocalización estructurada ni GIS (ADR 0031 §4).
    zona_afectada             varchar(300),

    -- Único salto sin retorno (ADR 0031 §4): una alerta finalizada no
    -- vuelve a estar vigente, si la situación se repite el municipio
    -- publica una alerta nueva.
    estado                    varchar(15)    not null default 'VIGENTE'
        check (estado in ('VIGENTE', 'FINALIZADA')),

    -- Copia del actor que publica la alerta, no una relación con usuario:
    -- mismo criterio que publicado_por_nombre/email en obra_publica (V19,
    -- ADR 0013).
    publicado_por_nombre      varchar(150)   not null,
    publicado_por_email       varchar(200)   not null,

    creado_en                 timestamptz    not null default now(),
    actualizado_en            timestamptz    not null default now()
    -- Sin geolocalización estructurada, adjuntos ni motivo de finalización
    -- (ADR 0031 §7, Pendiente de definir): fuera de alcance a propósito.
);

-- Orden del listado.
create index alerta_defensa_civil_creado_en_idx on alerta_defensa_civil (creado_en desc);

-- Filtro más usado del portal público.
create index alerta_defensa_civil_estado_idx on alerta_defensa_civil (estado);

comment on table alerta_defensa_civil is
    'Alertas de Defensa Civil publicadas por este municipio, con nivel de severidad de la convención del SMN y finalización como único cambio de estado posible (R27, ADR 0031).';

create table recurso_defensa_civil (
    id                        bigint generated always as identity primary key,
    tipo                      varchar(20)    not null
        check (tipo in ('REFUGIO', 'PUNTO_DE_ENCUENTRO', 'CENTRO_DE_ACOPIO', 'OTRO')),

    nombre                    varchar(200)   not null,
    -- Texto libre, sin geolocalización estructurada ni GIS (ADR 0031 §5).
    direccion                 varchar(300)   not null,
    -- Capacidad física del lugar, no cantidad de personas identificadas:
    -- sin check de rango en la base, validado en el servicio (ADR 0031,
    -- Minimización de datos).
    capacidad                 integer,
    telefono_contacto         varchar(50),
    descripcion               text,

    -- Transición libre en ambos sentidos (ADR 0031 §5): un refugio se
    -- activa y se desactiva según la situación, no hay una progresión
    -- unidireccional que modelar.
    estado                    varchar(10)    not null default 'ACTIVO'
        check (estado in ('ACTIVO', 'INACTIVO')),

    publicado_por_nombre      varchar(150)   not null,
    publicado_por_email       varchar(200)   not null,

    creado_en                 timestamptz    not null default now(),
    actualizado_en            timestamptz    not null default now()
);

create index recurso_defensa_civil_creado_en_idx on recurso_defensa_civil (creado_en desc);
create index recurso_defensa_civil_estado_idx on recurso_defensa_civil (estado);

comment on table recurso_defensa_civil is
    'Recursos de Defensa Civil (refugios, puntos de encuentro, centros de acopio) registrados por este municipio, sin relación de esquema con alerta_defensa_civil (R27, ADR 0031).';

-- Catálogo de permisos: área "Defensa Civil". Un único permiso cubre alta
-- y cambio de estado de las dos entidades: no hay diferencia real de
-- sensibilidad entre Alertas y Recursos que justifique separarlo (ADR
-- 0031 §3). Se asigna a administrador y agente, mismo criterio que
-- eventos.gestionar (V26).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('defensacivil.gestionar', 'Defensa Civil', 'defensacivil', 'gestionar',
     'Publicar y finalizar alertas de Defensa Civil, y registrar y actualizar el estado de recursos (refugios, puntos de encuentro).');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'defensacivil.gestionar';
