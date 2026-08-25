-- Registro de auditoría del municipio (ADR 0013 §3): quién hizo qué y
-- cuándo, para lo que hoy dispara un evento de dominio (alta de usuario).
--
-- actor_nombre y actor_email son una copia del dato al momento del hecho,
-- no un join contra usuario: si el actor cambia de nombre o se desactiva
-- después, el registro histórico no tiene que cambiar con él. Por el mismo
-- motivo actor_id no lleva foreign key -es un dato informativo, no
-- referencial- y el registro sobrevive aunque el usuario que lo generó ya
-- no exista como tal.
--
-- entidad_id es texto a propósito: usuario.creado audita un id numérico
-- hoy, pero atar la columna al tipo de id de la primera entidad auditada
-- obligaría a una migración de tipo apenas aparezca una entidad con otro
-- tipo de identificador.
create table registro_auditoria (
    id           bigint generated always as identity primary key,
    ocurrido_en  timestamptz  not null default now(),
    actor_id     bigint       not null,
    actor_nombre varchar(150) not null,
    actor_email  varchar(200) not null,
    accion       varchar(100) not null,
    entidad_tipo varchar(60)  not null,
    entidad_id   varchar(100) not null,
    detalle      text         not null
);

-- La pantalla de auditoría lista todo ordenado por fecha descendente sin
-- paginado (fuera de alcance de esta rebanada); este índice es lo que
-- evita que ese `order by` recorra la tabla entera a medida que crece.
create index registro_auditoria_ocurrido_en_idx on registro_auditoria (ocurrido_en desc);

comment on table registro_auditoria is
    'Quién hizo qué y cuándo, dentro de este municipio (ADR 0013).';

-- Permiso nuevo (área "Administración", igual que usuarios.ver/roles.ver):
-- ver el registro de auditoría es información sensible del municipio, así
-- que se asigna solo al rol de sistema 'administrador'. El rol 'agente' se
-- deja deliberadamente sin él.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('auditoria.ver', 'Administración', 'auditoria', 'ver',
     'Ver el registro de auditoría del municipio.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, 'auditoria.ver'
from rol r
where r.codigo = 'administrador';
