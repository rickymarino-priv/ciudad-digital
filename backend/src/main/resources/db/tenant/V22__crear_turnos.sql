-- Turnos para actividades municipales: catálogo público de actividades
-- recreativas (deporte/cultura/turismo), franjas horarias con cupo, y
-- reserva pública anónima con decremento atómico de cupo (R22, ADR 0026).
--
-- Tres tablas, sin columna de tenant: viven en la base del municipio,
-- igual que obra_publica (V19)/arbol_urbano (V20)/programa_social (V21).
create table actividad (
    id                     bigint generated always as identity primary key,
    nombre                 varchar(150)   not null,
    -- Enum cerrado (ADR 0026 §1/§2): nunca salud ni un trámite administrativo.
    tipo                   varchar(20)    not null
        check (tipo in ('DEPORTE', 'CULTURA', 'TURISMO')),
    descripcion            text,
    -- Texto libre, sin catálogo fijo de sedes municipales (ADR 0026 §2, mismo
    -- criterio que ubicacion en obra_publica/arbol_urbano).
    ubicacion              varchar(300)   not null,
    estado                 varchar(15)    not null default 'ACTIVA'
        check (estado in ('ACTIVA', 'INACTIVA')),

    -- Copia del actor que publica la actividad, no una relación con usuario:
    -- mismo criterio que publicado_por_nombre/email en obra_publica (V19, ADR 0013).
    publicado_por_nombre  varchar(150)   not null,
    publicado_por_email   varchar(200)   not null,

    creado_en             timestamptz    not null default now(),
    actualizado_en        timestamptz    not null default now()
    -- Sin arancel, foto ni geolocalización (ADR 0026 §8): fuera de alcance a
    -- propósito.
);

create index actividad_creado_en_idx on actividad (creado_en desc);
create index actividad_estado_idx on actividad (estado);
create index actividad_tipo_idx on actividad (tipo);

comment on table actividad is
    'Catálogo público de actividades municipales de deporte, cultura y turismo (R22, ADR 0026).';

-- franja_horaria: una franja puntual de una actividad, con cupo.
-- cupo_disponible se inicializa igual a cupo_total al crearse (única
-- escritura directa) y de ahí en más solo lo modifica el UPDATE
-- condicional atómico de FranjaHorariaRepository#reservarUnLugar (ADR
-- 0026 §4) — el check de abajo es defensa en profundidad, no el
-- mecanismo principal.
create table franja_horaria (
    id                bigint      generated always as identity primary key,
    actividad_id      bigint      not null references actividad (id),

    fecha             date        not null,
    hora_inicio       time        not null,
    hora_fin          time        not null
        check (hora_fin > hora_inicio),

    cupo_total        integer     not null check (cupo_total > 0),
    cupo_disponible   integer     not null check (cupo_disponible >= 0),

    creado_en         timestamptz not null default now()
    -- Sin actualizado_en: esta rebanada no edita una franja ya creada (ADR
    -- 0026 §3) — el único campo que cambia después de creada
    -- (cupo_disponible) lo hace el mecanismo de reserva, no una edición
    -- administrativa.
);

create index franja_horaria_actividad_id_idx on franja_horaria (actividad_id);
create index franja_horaria_fecha_idx on franja_horaria (fecha, hora_inicio);

comment on table franja_horaria is
    'Franjas horarias de una actividad municipal, con cupo (R22, ADR 0026).';

-- turno: la reserva pública anónima de un vecino sobre una franja. La
-- restricción unique de abajo es la barrera real contra la reserva
-- duplicada bajo concurrencia (ADR 0026 §4); el chequeo temprano en
-- GestionDeReservas es solo una salida rápida para el caso común, sin
-- carrera.
create table turno (
    id                  bigint         generated always as identity primary key,
    franja_id           bigint         not null references franja_horaria (id),

    nombre_solicitante  varchar(150)   not null,
    dni_solicitante     varchar(20)    not null,
    -- Obligatorio: el municipio necesita poder avisar si la actividad se
    -- reprograma o cancela (mismo criterio que contacto en
    -- inscripcion_social, ADR 0025 §4).
    contacto            varchar(200)   not null,

    creado_en           timestamptz    not null default now(),

    constraint turno_franja_id_dni_solicitante_unq unique (franja_id, dni_solicitante)
    -- Sin estado ni cancelación (ADR 0026 §8): fuera de alcance a propósito.
);

create index turno_franja_id_idx on turno (franja_id);

comment on table turno is
    'Reservas públicas anónimas de vecinos sobre franjas horarias de actividades municipales (R22, ADR 0026).';

-- Catálogo de permisos: área "Cultura, Turismo y Deportes". Un único
-- permiso cubre publicar actividades, crear franjas, cambiar el estado de
-- una actividad y ver las reservas de una franja (ADR 0026 §6): el dato
-- de turno (nombre, DNI, contacto para una actividad recreativa) no tiene
-- la sensibilidad que justificó separar en dos permisos en Desarrollo
-- Social (ADR 0025 §7). Se asigna a administrador y agente, mismo
-- criterio que obras.gestionar/arbolado.gestionar.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('turnos.gestionar', 'Cultura, Turismo y Deportes', 'turnos', 'gestionar',
     'Publicar actividades municipales, crear sus franjas horarias, cambiar su estado y ver las reservas.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'turnos.gestionar';
