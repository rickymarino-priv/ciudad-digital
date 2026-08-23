# 0009 - Entitlement de módulos por contrato, con gating en backend

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

El producto se comercializa como canon base + módulos adicionales (ver
[modelo comercial](../../producto/modelo-comercial.md)): cada municipio
tiene contratado un subconjunto del [catálogo funcional](../../producto/catalogo-funcional.md).
El sistema necesita saber qué módulos tiene habilitado cada tenant y hacer
cumplir ese límite.

El [ADR 0007](0007-modelo-de-datos-del-tenant.md) ya previó almacenar los
módulos habilitados en la configuración del tenant, pero no definió dónde
ni cómo se aplica ese límite.

## Decisión

**Dónde se aplica**: el entitlement se hace cumplir en el **backend**,
mediante un guard/interceptor que rechaza requests dirigidos a módulos que
el tenant no tiene contratados. El frontend además oculta los módulos no
habilitados, pero eso es experiencia de usuario, no enforcement — por sí
solo dejaría la API expuesta.

El módulo (en el sentido de Spring Modulith,
[ADR 0003](0003-spring-modulith-para-el-backend.md)) es la unidad natural
de gating: coincide la unidad técnica con la unidad comercial.

**Desacople del estado de pago**: un módulo se deshabilita cuando termina
un contrato — decisión comercial deliberada y manual — **nunca de forma
automática por falta de pago**. Los municipios son organismos públicos con
ciclos de pago lentos dependientes de disponibilidad presupuestaria;
suspender un servicio en uso por un atraso de Tesorería es un riesgo
político, reputacional y contractual desproporcionado. Los atrasos se
manejan con visibilidad y alertas en la consola del proveedor.

## Alternativas consideradas

- **Solo ocultar los módulos en el frontend**: trivial de implementar pero
  no es enforcement real (la API sigue accesible). Insuficiente por sí
  solo.
- **Corte automático por falta de pago**: patrón habitual en SaaS, pero
  inadecuado para clientes del sector público argentino por las razones
  expuestas. Descartado explícitamente.

## Consecuencias

- El interceptor de resolución de tenant
  ([ADR 0004](0004-resolucion-de-tenant-por-subdominio.md)) es el lugar
  natural donde también resolver el entitlement: el tenant ya viene
  resuelto con su configuración en el mismo paso.
- El modelo de contrato (qué módulos, desde cuándo, hasta cuándo) vive en
  la base de control, junto al resto de los datos del tenant.
- La suspensión de un tenant o de un módulo requiere una acción
  administrativa explícita, lo que implica que la consola del proveedor
  debe registrar quién la ejecutó y cuándo.

## Pendiente de definir

- Granularidad del contrato: si se registra a nivel módulo con fechas de
  alta/baja individuales, o como un plan contratado con vigencia única.
- Si la consola del proveedor (cross-tenant) se despliega como aplicación
  separada del monolito, dado que es la única superficie que accede a datos
  de todos los municipios simultáneamente.
