-- Catálogo de permisos: área "Administración". Ver indicadores agregados
-- cruzando los módulos operativos del municipio es información de gestión
-- para quien dirige el municipio, no trabajo operativo cotidiano de un
-- agente de un área (ADR 0033 §5): se reserva a administrador, mismo
-- criterio que municipio.verContrato/auditoria.ver. No se asigna a agente.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('reportes.ver', 'Administración', 'reportes', 'ver',
     'Ver el tablero de indicadores agregados de los módulos operativos del municipio.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo = 'administrador'
  and p.codigo = 'reportes.ver';
