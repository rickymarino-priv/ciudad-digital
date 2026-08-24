-- Permiso del módulo de ejemplo (ADR 0012 §10).
--
-- 'ejemplo' no es funcionalidad de producto: es el sujeto de prueba del
-- mecanismo de entitlement. Este permiso existe únicamente para poder
-- probar de punta a punta que entitlement y permiso conviven en el mismo
-- endpoint sin que uno tape al otro, así que se lo agrega al catálogo con
-- la misma mecánica que tendría el primer módulo funcional real.
--
-- Se lo asigna solo al rol de sistema 'administrador'. El rol 'agente' se
-- deja deliberadamente sin él: es lo que permite testear el caso "el
-- municipio contrató el módulo, pero este usuario no tiene el permiso"
-- sin tener que armar un rol ad hoc para probarlo (ADR 0011).
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('ejemplo.usar', 'Demostración', 'ejemplo', 'usar',
     'Usar el eco del módulo de ejemplo (módulo de demostración del mecanismo de contratación).');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, 'ejemplo.usar'
from rol r
where r.codigo = 'administrador';
