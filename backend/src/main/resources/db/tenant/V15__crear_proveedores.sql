-- Registro de proveedores del municipio (backlog R14): alta pública sin
-- cuenta (mismo criterio que reclamos/mesa de entradas, ADR 0014 §1) y
-- consulta posterior por token de seguimiento (ADR 0017, tercer
-- consumidor junto con reclamo y expediente). "Documentación" en esta
-- rebanada es una declaración (checklist + texto libre), no un archivo:
-- no hay infraestructura de almacenamiento de archivos en el proyecto
-- todavía (ver spec CD-22).
create table proveedor (
    id                                    bigint generated always as identity primary key,
    razon_social                         varchar(200)  not null,
    -- Normalizado a "XX-XXXXXXXX-X" antes de guardar (ver GestionDeProveedores):
    -- así dos altas con el mismo CUIT en formatos de entrada distintos
    -- (con o sin guiones) no evaden la unicidad de abajo.
    cuit                                  varchar(13)   not null,
    rubro                                 varchar(30)   not null
        check (rubro in ('CONSTRUCCION', 'SERVICIOS', 'INSUMOS_Y_SUMINISTROS',
                          'PROFESIONALES', 'TECNOLOGIA', 'OTRO')),
    email_contacto                        varchar(200)  not null,
    telefono_contacto                     varchar(50)   not null,
    domicilio                             varchar(300)  not null,
    declara_constancia_afip               boolean       not null default false,
    declara_seguro_responsabilidad_civil  boolean       not null default false,
    declara_certificado_antecedentes      boolean       not null default false,
    documentacion_adicional               varchar(500),
    estado                                varchar(20)   not null default 'PENDIENTE'
        check (estado in ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    comentario_gestion                    varchar(1000),
    token_hash                            varchar(64)   not null,
    creado_en                             timestamptz   not null default now(),
    actualizado_en                        timestamptz   not null default now()
);

-- Único por base de tenant: "registro único de proveedores" (catálogo
-- funcional §4) es único dentro de cada municipio, no cross-tenant — cada
-- municipio tiene su propia base (ADR 0001), así que esto no exige ningún
-- chequeo cruzado.
create unique index proveedor_cuit_idx on proveedor (cuit);
create unique index proveedor_token_hash_idx on proveedor (token_hash);

comment on table proveedor is
    'Registro de proveedores del municipio, con documentación declarada y estado de aprobación (backlog R14).';

-- Catálogo de permisos: área "Proveedores". Revisar y aprobar/rechazar un
-- proveedor es una tarea operativa de gestión del día a día (como
-- reclamos.gestionar o mesaentradas), no un acto fiscal como
-- tasas.publicar (V14) — se asigna a AMBOS roles de sistema.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('proveedores.ver', 'Proveedores', 'proveedores', 'ver',
     'Ver el listado y el detalle de los proveedores registrados.'),
    ('proveedores.gestionar', 'Proveedores', 'proveedores', 'gestionar',
     'Aprobar o rechazar el registro de un proveedor.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo in ('administrador', 'agente')
  and p.codigo in ('proveedores.ver', 'proveedores.gestionar');
