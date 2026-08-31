-- Desarrollo Social: catálogo público de programas sociales e inscripción
-- pública anónima con datos personales minimizados (R21, ADR 0025).
--
-- Dos tablas, sin columna de tenant: viven en la base del municipio,
-- igual que obra_publica (V19) y arbol_urbano (V20). programa_social es
-- información institucional, sin ningún dato de una persona
-- identificable, mismo perfil de riesgo que obra_publica.
create table programa_social (
    id                          bigint generated always as identity primary key,
    nombre                      varchar(150)   not null,
    descripcion                 text,
    -- Texto libre descriptivo, no un motor de reglas (ADR 0025 §3).
    criterios_de_elegibilidad   text,
    estado                      varchar(15)    not null default 'ABIERTO'
        check (estado in ('ABIERTO', 'CERRADO')),

    -- Copia del actor que publica el programa, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en
    -- obra_publica (V19, ADR 0013).
    publicado_por_nombre        varchar(150)   not null,
    publicado_por_email         varchar(200)   not null,

    creado_en                   timestamptz    not null default now(),
    actualizado_en              timestamptz    not null default now()
    -- Sin monto de subsidio, cupo máximo ni fechas de convocatoria (ADR
    -- 0025): fuera de alcance a propósito.
);

create index programa_social_creado_en_idx on programa_social (creado_en desc);
create index programa_social_estado_idx on programa_social (estado);

comment on table programa_social is
    'Catálogo público de programas sociales de este municipio (R21, ADR 0025).';

-- inscripcion_social sí guarda datos personales de un vecino, con
-- minimización deliberada (ADR 0025 §4): nunca ingresos, comprobantes ni
-- composición nominal del grupo familiar, solo su cantidad.
create table inscripcion_social (
    id                                       bigint generated always as identity primary key,
    programa_id                              bigint         not null references programa_social (id),

    nombre_solicitante                       varchar(150)   not null,
    dni_solicitante                          varchar(20)    not null,
    -- A diferencia de contacto_del_vecino en reclamo, acá es obligatorio:
    -- el municipio necesita poder contactar a la familia para gestionar
    -- la ayuda (ADR 0025 §4).
    contacto                                 varchar(200)   not null,

    cantidad_integrantes_grupo_familiar      integer        not null check (cantidad_integrantes_grupo_familiar > 0),
    -- Categorías amplias autodeclaradas, nunca un monto de ingreso (ADR 0025 §4).
    situacion_declarada                      varchar(30)    not null
        check (situacion_declarada in
            ('DESOCUPADO', 'EMPLEO_INFORMAL', 'EMPLEO_FORMAL', 'JUBILADO_O_PENSIONADO', 'OTRO')),
    comentario_adicional                     varchar(2000),

    estado                                   varchar(15)    not null default 'RECIBIDA'
        check (estado in ('RECIBIDA', 'EN_EVALUACION', 'APROBADA', 'RECHAZADA')),

    -- Hash SHA-256 del token de seguimiento anónimo (ADR 0017 §2): el
    -- token en claro nunca se persiste.
    token_hash                               varchar(64)    not null,

    -- Resolución del Área de Desarrollo Social. resuelto_por_* es, otra
    -- vez, copia del actor, no una relación (ADR 0013/ADR 0025 §8).
    comentario_de_resolucion                 varchar(2000),
    resuelto_por_nombre                      varchar(150),
    resuelto_por_email                       varchar(200),
    resuelto_en                              timestamptz,

    creado_en                                timestamptz    not null default now(),
    actualizado_en                           timestamptz    not null default now()
    -- Sin ingresos, adjuntos ni datos de los integrantes del grupo
    -- familiar más allá de la cantidad (ADR 0025 §4): fuera de alcance a
    -- propósito.
);

create index inscripcion_social_creado_en_idx on inscripcion_social (creado_en desc);
create index inscripcion_social_programa_id_idx on inscripcion_social (programa_id);
create index inscripcion_social_estado_idx on inscripcion_social (estado);

-- Única: cada token de seguimiento identifica una única inscripción
-- (mismo criterio que reclamo, V12).
create unique index inscripcion_social_token_hash_idx on inscripcion_social (token_hash);

comment on table inscripcion_social is
    'Inscripciones de vecinos a programas sociales, con datos personales minimizados (R21, ADR 0025).';

-- Catálogo de permisos: área "Desarrollo Social". Dos permisos separados
-- por sensibilidad (ADR 0025 §7): gestionar el catálogo no toca dato
-- personal (administrador y agente, mismo criterio que
-- obras.gestionar/arbolado.gestionar); revisar inscripciones expone datos
-- personales sensibles y decide sobre una ayuda social, se reserva solo a
-- administrador — a propósito no se asigna también a agente.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('desarrollosocial.gestionarProgramas', 'Desarrollo Social', 'desarrollosocial', 'gestionarProgramas',
     'Publicar un programa social y abrir o cerrar su convocatoria.'),
    ('desarrollosocial.revisarInscripciones', 'Desarrollo Social', 'desarrollosocial', 'revisarInscripciones',
     'Ver las inscripciones a programas sociales, con sus datos personales, y resolverlas.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'desarrollosocial.gestionarProgramas';

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo = 'administrador' and p.codigo = 'desarrollosocial.revisarInscripciones';
