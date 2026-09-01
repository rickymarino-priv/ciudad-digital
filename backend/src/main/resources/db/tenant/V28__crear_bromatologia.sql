-- Bromatología: padrón público de comercios habilitados y su historial de
-- inspecciones, protegido (R28, ADR 0032).
--
-- Sin columna de tenant: viven en la base del municipio, igual que
-- alerta_defensa_civil/recurso_defensa_civil (V27). A diferencia de esas
-- dos, acá sí hay una clave foránea real entre las dos tablas: cada
-- inspección pertenece a un comercio concreto (ADR 0032 §1).
create table comercio_bromatologico (
    id                                bigint generated always as identity primary key,
    nombre                            varchar(200)  not null,
    rubro                             varchar(20)   not null
        check (rubro in ('VERDULERIA', 'CARNICERIA', 'PANADERIA', 'RESTAURANTE', 'ALMACEN', 'OTRO')),
    -- Texto libre, sin geolocalización estructurada ni GIS (ADR 0032 §2).
    direccion                         varchar(300)  not null,

    -- Nace siempre HABILITADO (ADR 0032 §2): registrarlo en el padrón
    -- significa que el municipio ya otorgó la habilitación inicial. Sin
    -- PATCH directo de este campo: la única vía de cambio es una
    -- inspección (ver inspeccion_bromatologica más abajo).
    estado                            varchar(10)   not null default 'HABILITADO'
        check (estado in ('HABILITADO', 'OBSERVADO', 'CLAUSURADO')),

    fecha_habilitacion                date          not null,
    -- Informativo en esta rebanada: sin vencimiento automático, no hay
    -- infraestructura de jobs/cron en el proyecto (ADR 0032 §2).
    fecha_vencimiento_habilitacion    date          not null,

    -- Copia del actor que registra el comercio, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en
    -- recurso_defensa_civil (V27, ADR 0013).
    publicado_por_nombre              varchar(150)  not null,
    publicado_por_email               varchar(200)  not null,

    creado_en                         timestamptz   not null default now(),
    actualizado_en                    timestamptz   not null default now()
    -- Sin titular/CUIT ni relación de esquema con proveedor (ADR 0032 §6,
    -- Pendiente de definir): fuera de alcance a propósito.
);

-- Orden del listado.
create index comercio_bromatologico_creado_en_idx on comercio_bromatologico (creado_en desc);

-- Filtros del padrón público.
create index comercio_bromatologico_estado_idx on comercio_bromatologico (estado);
create index comercio_bromatologico_rubro_idx on comercio_bromatologico (rubro);

comment on table comercio_bromatologico is
    'Padrón público de comercios bromatológicos de este municipio, con alta protegida y estado que solo cambia como efecto de una inspección (R28, ADR 0032).';

create table inspeccion_bromatologica (
    id                          bigint generated always as identity primary key,
    comercio_id                 bigint        not null references comercio_bromatologico (id),

    fecha                       date          not null,
    -- Mismo conjunto de valores que comercio_bromatologico.estado: no se
    -- define un segundo enum para el mismo concepto (ADR 0032 §3).
    resultado                   varchar(10)   not null
        check (resultado in ('HABILITADO', 'OBSERVADO', 'CLAUSURADO')),
    -- Texto libre del inspector, no público: ver ADR 0032, Contexto.
    observaciones                text,

    -- Copia del actor que la registra, no una relación con usuario
    -- (ADR 0013).
    inspeccionado_por_nombre    varchar(150)  not null,
    inspeccionado_por_email     varchar(200)  not null,

    -- Sin actualizado_en a propósito: append-only, no se edita ni se
    -- borra después de creada (ADR 0032 §3).
    creado_en                   timestamptz   not null default now()
);

-- Historial por comercio, orden por fecha descendente.
create index inspeccion_bromatologica_comercio_id_fecha_idx on inspeccion_bromatologica (comercio_id, fecha desc);

comment on table inspeccion_bromatologica is
    'Historial append-only de inspecciones de un comercio bromatológico, protegido (no público), que actualiza el estado del comercio como efecto de su alta (R28, ADR 0032).';

-- Catálogo de permisos: área "Bromatología". Un único permiso cubre alta
-- de comercio, alta de inspección y lectura del historial de
-- inspecciones: no hay dato personal de nadie identificable en ninguna de
-- las dos entidades que justifique separarlo (ADR 0032 §5). Se asigna a
-- administrador y agente, mismo criterio que defensacivil.gestionar (V27).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('bromatologia.gestionar', 'Bromatología', 'bromatologia', 'gestionar',
     'Registrar comercios bromatológicos, registrar inspecciones y leer su historial.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'bromatologia.gestionar';
