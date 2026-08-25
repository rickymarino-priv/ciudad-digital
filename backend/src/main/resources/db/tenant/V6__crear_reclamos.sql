-- Reclamos ciudadanos (311): baches, alumbrado, poda y arbolado,
-- recolección de residuos, animales sueltos (ADR 0014).
--
-- Sin columna de tenant: vive en la base del municipio, igual que
-- usuario/rol/permiso (V2) y registro_auditoria (V5). El estado es un
-- ciclo de vida fijo e igual para todos los municipios (ADR 0014 §3), así
-- que se representa con un check de valores, no con una tabla de un motor
-- de workflow configurable.
create table reclamo (
    id                 bigint generated always as identity primary key,
    categoria          varchar(30)  not null
        check (categoria in
            ('BACHE', 'ALUMBRADO', 'PODA_ARBOLADO', 'RESIDUOS', 'ANIMALES_SUELTOS', 'OTRO')),
    descripcion        text         not null,
    direccion          varchar(300) not null,
    -- Datos de contacto del vecino: opcionales y sin verificar, no hay
    -- cuenta que los respalde (ADR 0014 §4). Se guardan como dato
    -- informativo para poder volver a contactarlo, no como identidad.
    nombre_contacto    varchar(150),
    contacto           varchar(200),
    estado             varchar(20)  not null
        check (estado in ('NUEVO', 'EN_PROCESO', 'RESUELTO', 'RECHAZADO')),
    comentario_gestion text,
    creado_en          timestamptz  not null default now(),
    actualizado_en     timestamptz  not null default now()
);

-- El listado de gestión ordena por fecha de carga descendente, sin
-- paginado (fuera de alcance de esta rebanada); mismo criterio que
-- registro_auditoria_ocurrido_en_idx (V5).
create index reclamo_creado_en_idx on reclamo (creado_en desc);

comment on table reclamo is
    'Reclamos cargados por vecinos de este municipio, con o sin sesión (ADR 0014).';

-- Catálogo de permisos: área "Reclamos". A diferencia de ejemplo.usar y
-- auditoria.ver -asignados solo a 'administrador' a propósito, como
-- sujeto de prueba del mecanismo de "módulo contratado pero sin
-- permiso"-, acá se asignan a AMBOS roles de sistema: es funcionalidad
-- real que el personal de atención al vecino necesita operar desde el
-- día uno, no una demostración (ADR 0014 §8).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('reclamos.ver',        'Reclamos', 'reclamos', 'ver',
     'Ver el listado de reclamos cargados por los vecinos.'),
    ('reclamos.gestionar',  'Reclamos', 'reclamos', 'gestionar',
     'Cambiar el estado de un reclamo (en proceso, resuelto, rechazado).');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo in ('administrador', 'agente')
  and p.codigo in ('reclamos.ver', 'reclamos.gestionar');
