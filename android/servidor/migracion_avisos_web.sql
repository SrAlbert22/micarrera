-- Avisos web (Web Push) — 2026-10
-- Copia local de la migración aplicada en Supabase (proyecto ivtgsjnjpkcufjkrbvft).

-- El "token" de un navegador es su suscripción en JSON (endpoint + llaves), más largo que el de FCM.
alter table public.dispositivos drop constraint dispositivos_token_largo;
alter table public.dispositivos add constraint dispositivos_token_largo check (length(token) between 20 and 2048);

-- Minutos antes de cada clase para avisar (solo navegadores; Android avisa con sus propias alarmas). null = sin avisos de clase.
alter table public.dispositivos add column minutos_clase smallint
  constraint dispositivos_minutos_clase check (minutos_clase in (5, 10, 15, 30));

-- Para no avisar dos veces la misma clase el mismo día en el mismo navegador. Solo la usa el servidor (sin políticas = nadie más).
create table public.avisos_clase (
  horario_id uuid not null references public.horario(id) on delete cascade,
  fecha date not null,
  dispositivo text not null, -- sha-256 del token
  enviado timestamptz not null default now(),
  primary key (horario_id, fecha, dispositivo)
);
alter table public.avisos_clase enable row level security;

-- Cada 5 minutos: avisos de clase a los navegadores (misma clave de cron que sincronizar-aulas).
select cron.schedule('recordar-clases', '*/5 * * * *', $$
  select net.http_post(
    url := 'https://ivtgsjnjpkcufjkrbvft.supabase.co/functions/v1/recordar-clases',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-cron-key', (select valor from public.config_servidor where clave = 'cron')),
    body := '{}'::jsonb,
    timeout_milliseconds := 60000
  );
$$);
