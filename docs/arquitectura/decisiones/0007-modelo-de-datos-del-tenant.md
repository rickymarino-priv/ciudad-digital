# 0007 - Modelo de datos del tenant: columnas explícitas + configuración JSON

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

La base de control necesita almacenar, por cada municipio, los datos que el
resolver de tenant ([ADR 0004](0004-resolucion-de-tenant-por-subdominio.md)),
el proceso de aprovisionamiento ([ADR 0005](0005-aprovisionamiento-de-tenant.md))
y el theming ([ADR 0006](0006-theming-dinamico-por-tokens.md)) necesitan
consultar.

## Decisión

Modelo híbrido: columnas explícitas para lo estructural y estable, y una
columna de configuración en JSON para lo que cambia con frecuencia o es
específico de negocio.

**Columnas explícitas:**
- `id` (UUID)
- `slug`: identificador único (ej. `sanmartin`), usado para derivar el
  nombre de la base física (`tenant_sanmartin`) y el subdominio por defecto
- `nombre_municipio`: nombre para mostrar
- `subdominio`: normalmente igual al slug, editable si hace falta
  diferenciarlo
- `dominio_personalizado`: nullable, add-on de ADR 0004
- `estado`: `pendiente | aprovisionando | activo | suspendido | error`,
  ciclo de vida de ADR 0005
- `nombre_base_datos`: normalmente derivado del slug, explícito para
  permitir el caso excepcional de instancia dedicada (ver ADR 0005)
- `fecha_alta`

**Columna `config` (JSON):**
- Tema visual: colores, logo, favicon, tipografía
- Módulos habilitados: qué módulos del
  [catálogo funcional](../../producto/catalogo-funcional.md) tiene activos
  ese municipio según lo contratado

## Alternativas consideradas

- **Todo en columnas explícitas**: más type-safe y fácil de consultar/
  filtrar, pero cada atributo nuevo de tema o de negocio requiere una
  migración de la base de control. Descartado por el costo de fricción a
  medida que el tema visual o los planes comerciales evolucionen.
- **Todo en una columna JSON**: máxima flexibilidad, pero pierde validación
  a nivel de base de datos y complica consultas estructurales (ej. "qué
  tenants están en estado `error`"). Descartado para los atributos que sí
  se consultan/filtran con frecuencia.

## Consecuencias

- No hace falta almacenar credenciales de base de datos por tenant: como
  todas las bases comparten host/usuario/password de aplicación (ADR 0005,
  bases lógicas compartidas en un mismo motor), el datasource router arma
  la conexión combinando esas credenciales compartidas con
  `nombre_base_datos` de cada fila. Esto evita tener que cifrar
  credenciales por tenant en el caso general.
- El campo `config` (módulos habilitados) es lo que le da sentido operativo
  a la modularidad comercial del producto: no todos los tenants tienen los
  mismos módulos activos.
- Si el caso excepcional de instancia dedicada ([ADR 0005](0005-aprovisionamiento-de-tenant.md))
  se vuelve frecuente, este modelo necesita revisarse para volver a
  incorporar credenciales por tenant.
