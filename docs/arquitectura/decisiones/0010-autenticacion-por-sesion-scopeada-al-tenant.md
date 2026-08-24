# 0010 - Autenticación por sesión server-side, scopeada al tenant

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

Con el municipio resuelto por subdominio
([ADR 0004](0004-resolucion-de-tenant-por-subdominio.md)) y una base por
municipio ([ADR 0001](0001-multi-tenant-con-bd-por-tenant.md)), R3 tiene
que dejar entrar usuarios a su portal. La propiedad que no se puede
romper es la misma de siempre: **las credenciales de un municipio no
sirven en otro**, y una sesión abierta en un municipio no vale en el de al
lado.

Hay dos decisiones acopladas: dónde viven los usuarios y cómo se sostiene
la sesión.

Restricciones del contexto: portales públicos, sin equipo de operaciones
dedicado, una sola instancia de backend en esta etapa, y la obligación de
poder cortarle el acceso a alguien de inmediato (un agente municipal que
deja el cargo no puede seguir entrando hasta que venza un token).

## Decisión

**Los usuarios viven en la base del municipio**, no en una tabla central
con columna `tenant_id`. El aislamiento de credenciales no queda como una
condición que hay que acordarse de escribir en cada consulta: la base
donde se busca el usuario ya es la del municipio del request, resuelta por
el datasource ruteado. Un `where` olvidado no puede filtrar usuarios de
otro municipio porque no están ahí.

**La sesión es server-side**, sostenida por una cookie de sesión
`HttpOnly`, `SameSite=Lax`, `Secure` fuera de desarrollo, y **host-only**:
se emite sin atributo `Domain`, así que el browser la manda únicamente al
subdominio que la creó. La cookie de `sanmartin.gob.ar` nunca viaja a
`moron.gob.ar`.

Esa garantía es del browser, así que **el servidor la verifica igual**: la
sesión guarda el slug del municipio en el que se abrió, y cada request
compara ese slug con el tenant resuelto por `Host`. Si no coinciden, la
sesión se invalida y el request se rechaza. Es la única forma de que la
propiedad se sostenga también contra un cliente que no sea un browser.

El almacenamiento de sesiones es el de Servlet (en memoria) en esta etapa.
Migrar a un store compartido es un cambio de configuración, no de diseño.

Las contraseñas se guardan hasheadas con **bcrypt**. El hash lo produce el
`PasswordEncoder` de la aplicación, que vive en el paquete raíz junto a la
configuración de bases: lo necesitan tanto el módulo de acceso, para
verificar, como el de tenants, para sembrar el usuario administrador
durante el alta.

La API de administración cross-tenant (`/api/admin/**`) pasa a
autenticarse con un **usuario de plataforma**, guardado en la base de
control y separado de los usuarios de municipio, en reemplazo del token
compartido `ciudad.admin.token` que R2 dejó como provisorio.

## Alternativas consideradas

- **JWT stateless**: evita estado de sesión en el servidor y escala sin
  sticky sessions. Descartado por dos motivos concretos: la revocación
  real exige una lista negra —o sea, estado igual, pero peor—, y los
  permisos viajan adentro del token, así que un cambio de rol no tiene
  efecto hasta que el token vence. En un portal público sumar el token en
  `localStorage` agrega superficie de XSS que la cookie `HttpOnly` no
  tiene.
- **Spring Session persistido en la base de control**: sobrevive
  reinicios y escala sin sticky sessions desde el día uno. Descartado por
  ahora porque mete sesiones de todos los municipios en una tabla central
  —justo el tipo de dato cross-tenant que el diseño viene evitando— y
  porque todavía no hay un problema medido de escala que lo justifique.
- **Tabla de usuarios central con `tenant_id`**: permitiría que una misma
  persona use un solo usuario en varios municipios. Descartado: contradice
  el ADR 0001 y convierte el aislamiento de credenciales en una condición
  que hay que repetir en cada consulta. El caso de la persona que trabaja
  en dos municipios se resuelve con dos usuarios.
- **Identidad federada (OAuth/OIDC contra un IdP del municipio)**: es a
  dónde esto va a terminar yendo para los agentes municipales, pero
  requiere que cada municipio tenga IdP y que haya un flujo de alta por
  municipio. No se descarta: se difiere, y el modelo de usuario local
  queda como el mecanismo base sobre el que después se enchufa.

## Consecuencias

- Cerrarle el acceso a alguien es inmediato: se desactiva el usuario y la
  próxima request no encuentra sesión válida.
- Los permisos se leen de la base en cada request, así que un cambio de
  rol tiene efecto al instante, sin esperar vencimientos.
- Aparece estado en el servidor: mientras haya una sola instancia no hay
  problema, pero escalar horizontalmente va a requerir sticky sessions o
  un store compartido. Queda anotado, no resuelto.
- El alta de municipio ahora tiene que sembrar un usuario administrador,
  porque un municipio recién creado sin nadie que pueda entrar no sirve.
- El aislamiento de sesión queda cubierto por test: una sesión abierta en
  un municipio, presentada bajo el `Host` de otro, tiene que ser
  rechazada.

## Pendiente de definir

- Política de contraseñas (longitud mínima, recambio, bloqueo por
  intentos fallidos) y recuperación de contraseña por email, que depende
  del motor de notificaciones de R5.
- Rate limiting del login, que sigue siendo endurecimiento diferido según
  la regla del proyecto.
- Federación con el IdP del municipio.
- Segundo factor para usuarios con permisos sensibles.
