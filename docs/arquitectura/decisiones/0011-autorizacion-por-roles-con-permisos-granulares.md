# 0011 - Autorización por roles con permisos granulares por módulo

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

Un municipio no tiene dos tipos de usuario, tiene muchos: quien carga
reclamos no es quien los cierra, quien ve el padrón no es quien lo edita,
y el secretario de una área no debería tocar la de al lado. Con 30+
módulos previstos ([roadmap](../producto/roadmap-fases.md)), la cantidad
de combinaciones no se puede anticipar desde acá: cada municipio organiza
sus áreas distinto.

Además, cada municipio contrata módulos distintos
([ADR 0009](0009-modelo-comercial-y-entitlement.md)), así que el conjunto
de permisos que tiene sentido en un municipio no es el mismo que en otro.

## Decisión

Modelo de **roles con permisos granulares**, todo en la base del
municipio:

- Un **permiso** es una capacidad concreta del sistema, identificada por
  un código `modulo.accion` (`usuarios.administrar`, `reclamos.cerrar`) y
  agrupada por área. El catálogo de permisos lo define el sistema, no el
  municipio: un permiso existe porque hay código que lo consulta. Se
  siembra por migración.
- Un **rol** es un conjunto de permisos con nombre, y lo define cada
  municipio. El alta siembra dos roles de sistema —administrador del
  municipio y agente— para que el municipio arranque con algo usable, pero
  puede crear los suyos y editarlos.
- Un **usuario** tiene N roles, y sus permisos efectivos son la unión de
  los permisos de sus roles. No hay permisos asignados directamente al
  usuario: la excepción individual es exactamente lo que vuelve
  inauditable un esquema de permisos a los dos años.

La verificación es **en el backend, por permiso, en cada endpoint**. El
frontend usa los permisos para decidir qué muestra, pero eso es
comodidad, no seguridad: esconder un botón no protege nada.

Los roles de sistema no se pueden borrar ni quedar sin el permiso de
administrar usuarios: un municipio que se deja a sí mismo afuera de su
propia administración necesita que alguien entre a la base a arreglarlo.

Cuando llegue el entitlement de módulos (R4), el orden de evaluación es
**entitlement primero, permiso después**: un permiso sobre un módulo no
contratado no habilita nada. Tener el permiso `reclamos.cerrar` en un
municipio que no contrató reclamos no es un caso de borde, es lo que va a
pasar cada vez que un municipio dé de baja un módulo.

## Alternativas consideradas

- **Roles fijos en código** (un enum `ADMIN`/`AGENTE`): alcanza para la
  demo y para nada más. Cada módulo nuevo agregaría casos al enum, y el
  primer municipio que pida "que este usuario vea pero no edite" obliga a
  rehacer el modelo con datos en producción. Descartado.
- **Permisos asignados directamente al usuario, sin roles**: máxima
  flexibilidad y ninguna capacidad de responder "quién puede hacer esto".
  Descartado.
- **ACL por recurso** (Spring Security ACL, permisos sobre instancias
  concretas): lo que haría falta para "este agente ve solo los expedientes
  de su área". Es un modelo más caro en consultas y en administración, y
  todavía no hay ningún módulo funcional que lo pida. Se difiere: el
  modelo por permiso no lo impide, se agrega encima cuando aparezca el
  primer caso real.
- **Catálogo de permisos editable por el municipio**: no tiene sentido, un
  permiso solo existe si hay código que lo consulta.

## Consecuencias

- Agregar un módulo funcional implica agregar sus permisos al catálogo por
  migración, y decidir qué rol de sistema los recibe por defecto.
- La pantalla de administración de roles del municipio necesita mostrar el
  catálogo agrupado por área y módulo, porque una lista plana de cientos
  de permisos no es administrable.
- El chequeo por permiso en cada endpoint es verificable con tests, y hay
  que tratarlo como parte de la definición del endpoint, no como algo que
  se agrega después.
- Un usuario sin roles no puede hacer nada, que es el default correcto.

## Pendiente de definir

- Permisos por instancia o por área concreta (ACL), cuando aparezca el
  primer módulo que los necesite.
- Delegación temporal de permisos (reemplazo por licencia), que en un
  organismo público va a aparecer.
- Cómo se refleja en la UI un permiso que el usuario tiene pero cuyo
  módulo el municipio no contrató.
