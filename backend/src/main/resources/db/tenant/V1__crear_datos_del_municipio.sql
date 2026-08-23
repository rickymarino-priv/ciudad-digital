-- Esquema de la base de un municipio.
--
-- Estas migraciones corren contra la base propia de cada tenant, no contra
-- la base de control: acá viven los datos del municipio, que nunca se
-- mezclan con los de otro (ADR 0001).

create table datos_de_contacto (
    id        integer      primary key,
    direccion varchar(200) not null,
    telefono  varchar(50)  not null,
    email     varchar(200) not null,

    -- Fila única: son los datos de contacto del municipio dueño de esta
    -- base, no una lista de contactos.
    constraint datos_de_contacto_fila_unica check (id = 1)
);

comment on table datos_de_contacto is
    'Datos de contacto del municipio dueño de esta base. Una sola fila.';
