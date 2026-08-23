-- Municipios de prueba para R1 (dos municipios, dos marcas).
-- En R2 el alta pasa a hacerse por el módulo de aprovisionamiento y
-- estas filas dejan de sembrarse por migración.

insert into tenant (id, slug, nombre_municipio, subdominio, estado, nombre_base_datos, config)
values (
    '8f14e45f-ea3a-4b2c-9f1d-5b6c7d8e9f01',
    'sanmartin',
    'San Martín',
    'sanmartin',
    'ACTIVO',
    'tenant_sanmartin',
    '{
       "tema": {
         "colorPrimario": "#1B4F9C",
         "colorPrimarioContraste": "#FFFFFF",
         "colorAcento": "#8A5A00",
         "colorFondo": "#F4F6FA",
         "colorSuperficie": "#FFFFFF",
         "colorTexto": "#16181D",
         "colorTextoTenue": "#4A4F57",
         "tipografia": "Georgia, ''Times New Roman'', serif",
         "logoUrl": "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCIgcm9sZT0iaW1nIj48Y2lyY2xlIGN4PSIzMiIgY3k9IjMyIiByPSIzMCIgZmlsbD0iIzFCNEY5QyIvPjxwYXRoIGQ9Ik0zMiA4bDIyIDEwdjE0YzAgMTMtOSAyMi0yMiAyNi0xMy00LTIyLTEzLTIyLTI2VjE4eiIgZmlsbD0iIzJFNkZEMCIvPjx0ZXh0IHg9IjMyIiB5PSI0MSIgZm9udC1mYW1pbHk9Ikdlb3JnaWEsc2VyaWYiIGZvbnQtc2l6ZT0iMjIiIGZvbnQtd2VpZ2h0PSI3MDAiIGZpbGw9IiNGRkZGRkYiIHRleHQtYW5jaG9yPSJtaWRkbGUiPlNNPC90ZXh0Pjwvc3ZnPg=="
       },
       "modulosHabilitados": []
     }'::jsonb
);

insert into tenant (id, slug, nombre_municipio, subdominio, estado, nombre_base_datos, config)
values (
    '3c9a1b2d-7e4f-4a8b-b0c1-2d3e4f5a6b02',
    'moron',
    'Morón',
    'moron',
    'ACTIVO',
    'tenant_moron',
    '{
       "tema": {
         "colorPrimario": "#1F6B4A",
         "colorPrimarioContraste": "#FFFFFF",
         "colorAcento": "#8C3A00",
         "colorFondo": "#F3F8F5",
         "colorSuperficie": "#FFFFFF",
         "colorTexto": "#14201A",
         "colorTextoTenue": "#465049",
         "tipografia": "''Segoe UI'', system-ui, -apple-system, sans-serif",
         "logoUrl": "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCIgcm9sZT0iaW1nIj48Y2lyY2xlIGN4PSIzMiIgY3k9IjMyIiByPSIzMCIgZmlsbD0iIzFGNkI0QSIvPjxwYXRoIGQ9Ik0zMiA4bDIyIDEwdjE0YzAgMTMtOSAyMi0yMiAyNi0xMy00LTIyLTEzLTIyLTI2VjE4eiIgZmlsbD0iIzJFOEM2MyIvPjx0ZXh0IHg9IjMyIiB5PSI0MSIgZm9udC1mYW1pbHk9Ikdlb3JnaWEsc2VyaWYiIGZvbnQtc2l6ZT0iMjQiIGZvbnQtd2VpZ2h0PSI3MDAiIGZpbGw9IiNGRkZGRkYiIHRleHQtYW5jaG9yPSJtaWRkbGUiPk08L3RleHQ+PC9zdmc+"
       },
       "modulosHabilitados": []
     }'::jsonb
);
