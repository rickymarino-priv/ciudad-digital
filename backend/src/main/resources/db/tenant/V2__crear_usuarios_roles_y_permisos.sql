-- Usuarios, roles y permisos del municipio (ADR 0010, ADR 0011).
--
-- Viven en la base del municipio, no en una tabla central: así el
-- aislamiento de credenciales no depende de acordarse de filtrar por
-- municipio en cada consulta, sino de a qué base se está conectado.

create table usuario (
    id             bigint generated always as identity primary key,
    nombre         varchar(150) not null,
    email          varchar(200) not null,
    hash_password  varchar(100) not null,
    activo         boolean      not null default true,
    creado_en      timestamptz  not null default now(),
    ultimo_acceso  timestamptz
);

-- El email identifica al usuario y se escribe como cada uno lo escriba,
-- así que la unicidad no puede ser sensible a mayúsculas: sin esto
-- Ana@… y ana@… serían dos usuarios distintos que compiten por el mismo
-- login.
create unique index usuario_email_unico on usuario (lower(email));

comment on table usuario is
    'Personas que pueden entrar al portal de este municipio.';

-- Catálogo de permisos: lo define el sistema, no el municipio. Un permiso
-- existe porque hay código que lo consulta (ADR 0011), así que se siembra
-- por migración y crece con cada módulo funcional.
create table permiso (
    codigo      varchar(100) primary key,
    area        varchar(60)  not null,
    modulo      varchar(60)  not null,
    accion      varchar(60)  not null,
    descripcion varchar(300) not null,

    -- El código es la forma canónica de modulo.accion: si no coinciden,
    -- el catálogo miente y las consultas por área devuelven cualquier cosa.
    constraint permiso_codigo_coherente check (codigo = modulo || '.' || accion)
);

comment on table permiso is
    'Capacidades que el sistema sabe verificar. Lo siembra la migración.';

-- Los roles sí los define cada municipio, salvo los de sistema, que vienen
-- sembrados para que un municipio recién dado de alta sea usable.
create table rol (
    id          bigint generated always as identity primary key,
    codigo      varchar(60)  not null,
    nombre      varchar(100) not null,
    descripcion varchar(300),
    del_sistema boolean      not null default false
);

create unique index rol_codigo_unico on rol (lower(codigo));

comment on column rol.del_sistema is
    'Rol sembrado en el alta. No se borra: un municipio no puede quedarse sin administrador.';

create table rol_permiso (
    rol_id         bigint       not null references rol (id) on delete cascade,
    permiso_codigo varchar(100) not null references permiso (codigo),

    primary key (rol_id, permiso_codigo)
);

create table usuario_rol (
    usuario_id bigint not null references usuario (id) on delete cascade,
    rol_id     bigint not null references rol (id) on delete cascade,

    primary key (usuario_id, rol_id)
);

-- --- Catálogo inicial de permisos ---
--
-- Solo los que hoy tienen código que los verifica. Los permisos de los
-- módulos funcionales llegan con cada módulo, en su propia migración.

insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('usuarios.ver',         'Administración', 'usuarios', 'ver',
     'Ver la lista de usuarios del municipio.'),
    ('usuarios.administrar', 'Administración', 'usuarios', 'administrar',
     'Crear usuarios, editarlos, activarlos y desactivarlos.'),
    ('roles.ver',            'Administración', 'roles',    'ver',
     'Ver los roles del municipio y los permisos de cada uno.'),
    ('roles.administrar',    'Administración', 'roles',    'administrar',
     'Crear roles, editarlos y cambiar sus permisos.');

-- --- Roles de sistema ---

insert into rol (codigo, nombre, descripcion, del_sistema) values
    ('administrador', 'Administrador del municipio',
     'Administra los usuarios y los roles del municipio.', true),
    ('agente', 'Agente municipal',
     'Personal del municipio sin tareas de administración.', true);

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo = 'administrador';

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, 'usuarios.ver'
from rol r
where r.codigo = 'agente';
