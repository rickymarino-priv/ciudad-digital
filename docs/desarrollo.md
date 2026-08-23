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

Flyway crea el esquema de la base de control y siembra los dos municipios
de prueba en el primer arranque.

Frontend, en <http://localhost:5173>:

```bash
cd frontend && npm install && npm run dev
```

## Ver los portales

El municipio se resuelve por subdominio (ADR 0004), así que **no** se entra
por `localhost` sino por el subdominio de cada uno:

- <http://sanmartin.localhost:5173>
- <http://moron.localhost:5173>

Los dominios `*.localhost` resuelven solos en Linux y macOS; no hace falta
tocar `/etc/hosts`. Entrar por `localhost` a secas devuelve 404 a propósito:
el dominio base no es ningún municipio.

## Tests

```bash
cd backend && ./mvnw test
```

Levantan Postgres con Testcontainers, así que Docker tiene que estar
disponible. Incluyen la verificación de límites entre módulos de Spring
Modulith y el test de aislamiento entre municipios.

## Build del frontend

```bash
cd frontend && npm run build
```
