-- Registro persistente de publicaciones de eventos de Spring Modulith
-- (ADR 0013 §1, §2): qué evento se publicó, para qué listener, y si ya
-- terminó de procesarlo. Si el listener falla, la fila queda sin completar,
-- visible para diagnóstico manual vía consulta directa a esta tabla, pero
-- sin reintento automático en esta rebanada (ADR 0013 §2): esta app no
-- habilita spring.modulith.events.republish-outstanding-events-on-restart
-- porque, en una arquitectura de una base por tenant, no hay ninguna
-- consulta sin tenant resuelto que pueda encontrar algo que reintentar.
--
-- Vive en la base de cada municipio, no en la de control: todo evento hoy
-- nace de una acción dentro del portal de un municipio, y una tabla
-- compartida mezclaría en un solo lugar el evento serializado -incluidos
-- datos personales, como el email del usuario creado- de todos los
-- municipios (ADR 0013 §1, ADR 0001).
--
-- El DDL no se adivina: sale de generar el esquema real con la utilidad de
-- esquema de Jakarta Persistence (schema-generation scripts action, sin
-- base viva) contra la entidad del starter (spring-modulith-events-jpa
-- 2.1.0, org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication),
-- con el mismo dialecto de Postgres que usa tenantEntityManagerFactory.
-- Esa entidad no declara nombres de columna explícitos, así que Hibernate
-- usa el nombre literal de cada atributo Java (completionAttempts,
-- listenerId, etc.) y los emite sin comillas: Postgres los pliega a
-- minúsculas sin guion bajo (completionattempts, listenerid...). Es la
-- misma resolución que hace Hibernate en cada consulta que genera, así que
-- estos nombres se dejan tal cual salieron -sin pasarlos a snake_case, a
-- diferencia del resto de este esquema- para que ambos coincidan.
create table event_publication (
    id                    uuid         not null primary key,
    completionAttempts    integer      not null,
    completionDate        timestamptz,
    eventType             varchar(255) not null,
    lastResubmissionDate  timestamptz,
    listenerId            varchar(255) not null,
    publicationDate       timestamptz  not null,
    -- El evento serializado (JSON) no tiene un largo acotado de antemano:
    -- un varchar(255) truncaría cualquier evento con más de un par de
    -- campos. Es el único ajuste sobre el DDL generado (ADR 0013 §1).
    serializedEvent       text         not null,
    status                varchar(255)
        check (status in ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);

comment on table event_publication is
    'Registro persistente de publicaciones de eventos de Spring Modulith (ADR 0013). Lo gestiona el starter; no se pobla ni se lee a mano.';
