-- Mesa de Entradas: sumar habilitación comercial simple y permiso de obra
-- menor al catálogo de trámites (backlog R10, ADR 0016), completando el
-- subset de Trámites a Distancia de Fase 1.

-- domicilio_a_certificar deja de ser obligatorio a nivel de columna: ya
-- no es el único tipo de trámite. Sigue siendo obligatorio para las filas
-- de CERTIFICADO_DOMICILIO vía el check agregado más abajo (ADR 0016).
alter table expediente alter column domicilio_a_certificar drop not null;

-- Ampliar los catálogos de tipo/estado. Nombres de constraint verificados
-- contra el esquema real (información de information_schema.check_constraints
-- sobre una base migrada con V1-V9): coinciden con el patrón que Postgres
-- autogenera para un `check` inline sin nombre explícito en el `create
-- table` original (V9).
alter table expediente drop constraint expediente_tipo_check;
alter table expediente add constraint expediente_tipo_check
    check (tipo in ('CERTIFICADO_DOMICILIO', 'HABILITACION_COMERCIAL_SIMPLE', 'PERMISO_OBRA_MENOR'));

alter table expediente drop constraint expediente_estado_check;
alter table expediente add constraint expediente_estado_check
    check (estado in ('INICIADO', 'EN_REVISION', 'INSPECCION', 'APROBADO', 'RECHAZADO'));

alter table movimiento_de_expediente drop constraint movimiento_de_expediente_estado_anterior_check;
alter table movimiento_de_expediente add constraint movimiento_de_expediente_estado_anterior_check
    check (estado_anterior in ('INICIADO', 'EN_REVISION', 'INSPECCION', 'APROBADO', 'RECHAZADO'));

alter table movimiento_de_expediente drop constraint movimiento_de_expediente_estado_nuevo_check;
alter table movimiento_de_expediente add constraint movimiento_de_expediente_estado_nuevo_check
    check (estado_nuevo in ('INICIADO', 'EN_REVISION', 'INSPECCION', 'APROBADO', 'RECHAZADO'));

-- Datos propios de habilitación comercial simple (ADR 0016): columnas
-- explícitas nullable, obligatorias solo para su tipo (check al final).
alter table expediente add column rubro_comercial varchar(200);
alter table expediente add column direccion_local varchar(300);

-- Datos propios de permiso de obra menor (ADR 0016).
alter table expediente add column direccion_obra varchar(300);
alter table expediente add column descripcion_obra varchar(500);

-- Cada tipo exige sus propios campos, y solo los suyos (ADR 0016): reemplaza,
-- para el dato propio de trámite, la garantía que antes daba el "not null"
-- de domicilio_a_certificar cuando era la única columna posible. Cada rama
-- exige además que los campos de los OTROS dos tipos sean null: sin eso, un
-- alta con campos de más (de otro tipo) pasaba el check igual y quedaba
-- persistida con datos de un tipo que no le corresponden.
alter table expediente add constraint expediente_datos_por_tipo_check check (
    (tipo = 'CERTIFICADO_DOMICILIO'
        and domicilio_a_certificar is not null
        and rubro_comercial is null and direccion_local is null
        and direccion_obra is null and descripcion_obra is null)
    or (tipo = 'HABILITACION_COMERCIAL_SIMPLE'
        and domicilio_a_certificar is null
        and rubro_comercial is not null and direccion_local is not null
        and direccion_obra is null and descripcion_obra is null)
    or (tipo = 'PERMISO_OBRA_MENOR'
        and domicilio_a_certificar is null and rubro_comercial is null and direccion_local is null
        and direccion_obra is not null and descripcion_obra is not null)
);

comment on column expediente.rubro_comercial is
    'Dato propio de HABILITACION_COMERCIAL_SIMPLE (ADR 0016). Null para los demás tipos.';
comment on column expediente.direccion_local is
    'Dato propio de HABILITACION_COMERCIAL_SIMPLE (ADR 0016). Null para los demás tipos.';
comment on column expediente.direccion_obra is
    'Dato propio de PERMISO_OBRA_MENOR (ADR 0016). Null para los demás tipos.';
comment on column expediente.descripcion_obra is
    'Dato propio de PERMISO_OBRA_MENOR (ADR 0016). Null para los demás tipos.';
