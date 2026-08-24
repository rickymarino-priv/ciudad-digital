# Entorno de desarrollo

## Requisitos

- JDK 21
- Node LTS (probado con 24.x)
- Docker con el plugin `compose`

En Linux, si `docker ps` falla con `permission denied` sobre el socket, hay
que estar en el grupo `docker` (`sudo usermod -aG docker $USER`) **y volver
a iniciar sesión**: el cambio de grupo no lo toman los procesos ya
corriendo, incluidas las terminales abiertas.

## Levantar el entorno

Postgres (base de control):

```bash
docker compose up -d
```

Backend, en <http://localhost:8080>:

```bash
cd backend && ./mvnw spring-boot:run
```

Flyway crea el esquema de la base de control en el primer arranque. **No
hay ningún municipio hasta que se le da el alta**: los municipios ya no se
siembran por migración, se crean por la API (ADR 0005).

Frontend, en <http://localhost:5173>:

```bash
cd frontend && npm install && npm run dev
```

## Dar de alta un municipio

El alta crea la base de datos del municipio, la migra y la siembra —datos
de contacto y **usuario administrador**—. Es una operación cross-tenant,
así que va por la API de administración, protegida con el token de
`ciudad.admin.token`:

```bash
curl -X POST http://localhost:8080/api/admin/municipios \
  -H "X-Admin-Token: cambiar-en-r3" \
  -H "Content-Type: application/json" \
  -d '{
    "slug": "sanmartin",
    "nombreMunicipio": "San Martín",
    "direccion": "Av. Ricardo Balbín 1550",
    "telefono": "0800-333-7626",
    "email": "contacto@sanmartin.gob.ar",
    "administrador": {
      "nombre": "Ana Gómez",
      "email": "ana.gomez@sanmartin.gob.ar",
      "password": "una-contrasena-larga"
    },
    "tema": {
      "colorPrimario": "#1B4F9C",
      "colorPrimarioContraste": "#FFFFFF",
      "colorAcento": "#8A5A00",
      "colorFondo": "#F4F6FA",
      "colorSuperficie": "#FFFFFF",
      "colorTexto": "#16181D",
      "colorTextoTenue": "#4A4F57",
      "tipografia": "Georgia, serif",
      "logoUrl": "data:image/svg+xml;base64,..."
    }
  }'
```

Estado de todos los municipios, con la versión de esquema que tiene
aplicada la base de cada uno:

```bash
curl http://localhost:8080/api/admin/municipios -H "X-Admin-Token: cambiar-en-r3"
```

El token es una medida provisoria: se reemplaza por un usuario de
plataforma dentro de R3. Un token compartido no identifica a nadie, así que
no sirve para auditar quién dio de alta un municipio.

### Migrar los municipios que ya existen

Con una base por municipio, un release nuevo no se aplica solo: las bases
existentes se quedan en la versión anterior hasta que alguien las migra.

```bash
curl -X POST http://localhost:8080/api/admin/municipios/migraciones \
  -H "X-Admin-Token: cambiar-en-r3"
```

Devuelve la versión en la que quedó cada municipio, y el error de los que
hayan fallado —sin interrumpir a los demás—.

### Recuperar el acceso a un municipio

Los municipios dados de alta antes de R3 no tienen ningún usuario, y
cualquier municipio puede quedarse sin administrador. Para esos casos:

```bash
curl -X POST http://localhost:8080/api/admin/municipios/sanmartin/administrador \
  -H "X-Admin-Token: cambiar-en-r3" \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Ana Gómez",
    "email": "ana.gomez@sanmartin.gob.ar",
    "password": "una-contrasena-larga"
  }'
```

## Ver los portales

El municipio se resuelve por subdominio (ADR 0004), así que **no** se entra
por `localhost` sino por el subdominio de cada uno:

- <http://sanmartin.localhost:5173>
- <http://moron.localhost:5173>

Los dominios `*.localhost` resuelven solos en Linux y macOS; no hace falta
tocar `/etc/hosts`. Entrar por `localhost` a secas devuelve 404 a propósito:
el dominio base no es ningún municipio.

## Entrar al portal

El botón "Iniciar sesión" del portal abre la pantalla de ingreso. La sesión
queda atada al municipio en el que se abrió: la misma cookie presentada en
el subdominio de otro municipio se rechaza y la sesión se cierra
([ADR 0010](arquitectura/decisiones/0010-autenticacion-por-sesion-scopeada-al-tenant.md)).

Desde la consola, la sesión necesita el token CSRF que el backend deja en
una cookie:

```bash
curl -c /tmp/cookies -b /tmp/cookies http://sanmartin.localhost:8080/api/sesion

curl -c /tmp/cookies -b /tmp/cookies \
  -X POST http://sanmartin.localhost:8080/api/sesion \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $(grep XSRF-TOKEN /tmp/cookies | cut -f7)" \
  -d '{"email": "ana.gomez@sanmartin.gob.ar", "password": "una-contrasena-larga"}'
```

El usuario administrador que sembró el alta arranca con todos los permisos
de administración
([ADR 0011](arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)).

## Tests

```bash
cd backend && ./mvnw test
```

Levantan Postgres con Testcontainers, así que Docker tiene que estar
disponible. Los municipios de prueba se dan de alta por la API real, con
creación de base incluida. Incluyen la verificación de límites entre
módulos de Spring Modulith y los tests de aislamiento entre municipios.

Si se agrega o elimina una migración, conviene correr `./mvnw clean test`:
Maven no borra de `target/classes` los recursos que ya no existen en el
código, y Flyway terminaría aplicando una migración eliminada.

## Build del frontend

```bash
cd frontend && npm run build
```
