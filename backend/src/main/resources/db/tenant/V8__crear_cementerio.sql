-- Registro de sepulturas del cementerio municipal: parcelas, nichos y
-- panteones, con búsqueda pública por nombre del difunto (backlog R8).
--
-- Sin columna de tenant: vive en la base del municipio, igual que
-- reclamo (V6) y norma (V7).
create table sepultura (
    id                     bigint generated always as identity primary key,
    tipo_parcela           varchar(20)  not null
        check (tipo_parcela in ('NICHO', 'PANTEON', 'PARCELA', 'BOVEDA')),
    sector                 varchar(100) not null,
    fila                   varchar(50),
    numero                 varchar(50)  not null,
    nombre_difunto         varchar(200) not null,
    fecha_fallecimiento    date         not null,
    fecha_inhumacion       date         not null,
    -- Titular de la concesión y su contacto: privados, no se exponen en
    -- la búsqueda pública (minimización de datos de terceros vivos). Solo
    -- se devuelven en la respuesta del alta, a quien lo acaba de cargar.
    nombre_titular         varchar(200),
    contacto_titular       varchar(200),
    observaciones          text,
    -- Copia del actor al momento de registrar, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en norma
    -- (V7, ADR 0013).
    registrado_por_nombre  varchar(150) not null,
    registrado_por_email   varchar(200) not null,
    creado_en              timestamptz  not null default now()
    -- Sin estado ni motor de workflow: registrar una sepultura no tiene
    -- transiciones, es un alta y listo (a diferencia de reclamo, V6).
);

-- La búsqueda pública es principalmente por nombre del difunto
-- (alfabética, como una guía telefónica); a diferencia de
-- reclamo_creado_en_idx (V6) y norma_fecha_publicacion_idx (V7), acá el
-- orden natural no es temporal.
create index sepultura_nombre_difunto_idx on sepultura (nombre_difunto);

comment on table sepultura is
    'Registros de inhumación del cementerio municipal de este municipio (backlog R8).';

-- Catálogo de permisos: área "Cementerio". Igual criterio que
-- reclamos.ver/reclamos.gestionar (V6): es funcionalidad operativa real
-- que el personal del cementerio necesita desde el día uno, no un acto
-- legal como publicar una norma (boletin.publicar, V7, solo
-- administrador) — se asigna a AMBOS roles de sistema.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('cementerio.registrar', 'Cementerio', 'cementerio', 'registrar',
     'Registrar una sepultura (inhumación) en el cementerio municipal.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo in ('administrador', 'agente')
  and p.codigo = 'cementerio.registrar';
