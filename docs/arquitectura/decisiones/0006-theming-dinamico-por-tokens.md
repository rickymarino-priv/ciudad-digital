# 0006 - Theming del frontend por tokens dinámicos servidos desde la base de control

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

El producto es multi-tenant en frontend, con identidad visual distinta por
municipio (decisión de producto, ver
[vision-y-alcance.md](../../producto/vision-y-alcance.md)). Con equipo
chico, mantener un build de frontend por tenant no es sostenible a medida
que crece la cantidad de municipios.

## Decisión

El frontend es una única aplicación (una sola build) para todos los
tenants. La identidad visual (colores, logo, favicon, tipografía) se
resuelve en runtime a partir de **tokens de diseño** (CSS custom
properties) aplicados según el tenant resuelto
([ADR 0004](0004-resolucion-de-tenant-por-subdominio.md)).

La configuración de tema vive en la **base de control** (no en la base
operativa de cada tenant), porque el resolver de tenant ya consulta esa
base en cada request — el tema sale de la misma consulta, sin abrir una
segunda conexión a la base del municipio.

Para el MVP, los tokens se obtienen vía **fetch client-side** al arrancar
el frontend (llamada a `/api/tenant/tema` según el subdominio resuelto) y
se aplican como variables CSS. Se acepta el flash breve sin branding
correcto antes de que responda la llamada, como costo del MVP.

## Alternativas consideradas

- **Build white-label por tenant**: descartado por no escalar con la
  cantidad de municipios prevista y por multiplicar el trabajo de CI/CD.
- **Micro-frontends con shell compartido**: solo se justifica ante
  diferencias *funcionales* entre tenants, no visuales — no es el caso acá.
- **Inyección de tokens en el HTML inicial** (sin flash): mejor
  experiencia, pero depende de una decisión de framework/SSR todavía no
  tomada. Queda como mejora futura, no bloqueante para el MVP.

## Consecuencias

- Un único pipeline de build y despliegue de frontend para todos los
  tenants.
- La base de control necesita almacenar los atributos de tema por tenant
  (se define en el modelo de datos del tenant).
- Si el flash de marca resulta un problema real de percepción, se resuelve
  más adelante con inyección server-side, sin rediseñar el mecanismo de
  tokens.
