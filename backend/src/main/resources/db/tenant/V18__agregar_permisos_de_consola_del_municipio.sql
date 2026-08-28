-- Catálogo de permisos: área "Administración". Ver el contrato y el
-- historial de solicitudes, y crear una solicitud nueva, es información
-- contractual/comercial del municipio, no trabajo operativo cotidiano: se
-- reserva a administrador, igual criterio que la administración de
-- usuarios y roles (ADR 0022 §4). No se asigna a agente.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('municipio.verContrato', 'Administración', 'municipio', 'verContrato',
     'Ver los módulos contratados, el tramo poblacional, el estado de facturación '
     'y las solicitudes de alta/baja de módulo del municipio.'),
    ('municipio.solicitarModulo', 'Administración', 'municipio', 'solicitarModulo',
     'Solicitar el alta o la baja de un módulo contratado.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo = 'administrador'
  and p.codigo in ('municipio.verContrato', 'municipio.solicitarModulo');
