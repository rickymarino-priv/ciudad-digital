-- Mesa de Entradas: motor de expediente/workflow mínimo (ADR 0015) y su
-- primer trámite, certificado de domicilio (backlog R9).
--
-- Sin columna de tenant: vive en la base del municipio, igual que
-- reclamo/norma/sepultura. El circuito de estados es fijo por tipo de
-- trámite, definido en código (TipoDeTramite/CircuitoDeTramite,
-- ADR 0015 §1), no editable por el municipio.
create table expediente (
    id                     bigint generated always as identity primary key,
    tipo                   varchar(40)  not null
        check (tipo in ('CERTIFICADO_DOMICILIO')),
    estado                 varchar(20)  not null
        check (estado in ('INICIADO', 'EN_REVISION', 'APROBADO', 'RECHAZADO')),
    solicitante_nombre     varchar(200) not null,
    solicitante_contacto   varchar(200),
    -- Único dato propio del tipo de trámite hoy: columna explícita, no
    -- JSON de datos variables (ADR 0015 §3 — se decide con un segundo
    -- tipo real delante, no antes).
    domicilio_a_certificar varchar(300) not null,
    creado_en              timestamptz  not null default now(),
    actualizado_en         timestamptz  not null default now()
);

create index expediente_creado_en_idx on expediente (creado_en desc);

comment on table expediente is
    'Trámites iniciados en Mesa de Entradas de este municipio (backlog R9, ADR 0015).';

-- Historial de cambios de estado: quién lo hizo y cuándo (ADR 0015 §2).
-- El primer movimiento (alta) tiene estado_anterior/actor_* en null: no
-- hay estado previo, y el alta es pública y anónima (ADR 0014 §1,
-- reutilizado acá), sin actor autenticado que la firme.
create table movimiento_de_expediente (
    id              bigint generated always as identity primary key,
    expediente_id   bigint      not null references expediente(id),
    estado_anterior varchar(20)
        check (estado_anterior in ('INICIADO', 'EN_REVISION', 'APROBADO', 'RECHAZADO')),
    estado_nuevo    varchar(20) not null
        check (estado_nuevo in ('INICIADO', 'EN_REVISION', 'APROBADO', 'RECHAZADO')),
    actor_nombre    varchar(150),
    actor_email     varchar(200),
    comentario      text,
    fecha           timestamptz not null default now()
);

create index movimiento_de_expediente_expediente_id_idx on movimiento_de_expediente (expediente_id);

comment on table movimiento_de_expediente is
    'Historial de cambios de estado de cada expediente: quién lo hizo y cuándo (ADR 0015 §2).';

-- Catálogo de permisos: área "Mesa de Entradas". Igual criterio que
-- reclamos.ver/reclamos.gestionar (V6): funcionalidad operativa real del
-- personal de Mesa de Entradas desde el día uno, se asigna a AMBOS roles
-- de sistema.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('mesaentradas.ver',       'Mesa de Entradas', 'mesaentradas', 'ver',
     'Ver el listado de trámites iniciados en Mesa de Entradas.'),
    ('mesaentradas.gestionar', 'Mesa de Entradas', 'mesaentradas', 'gestionar',
     'Avanzar el estado de un trámite de Mesa de Entradas.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo in ('administrador', 'agente')
  and p.codigo in ('mesaentradas.ver', 'mesaentradas.gestionar');
