-- FamilyTV: حسابات الأعضاء مع ربط كل حساب بجهاز واحد.
-- شغّل هذا الملف كاملاً مرة واحدة في Supabase > SQL Editor.
-- الجداول مغلقة تماماً أمام مفتاح التطبيق (RLS بلا سياسات)، والوصول كله عبر الدوال أدناه.

create extension if not exists pgcrypto with schema extensions;

create table if not exists public.fm_admin (
  id int primary key default 1 check (id = 1),
  secret_hash text not null,
  fail_count int not null default 0,
  locked_until timestamptz
);
alter table public.fm_admin enable row level security;

create table if not exists public.fm_members (
  name text primary key,
  pass_hash text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz,                 -- null = بلا انتهاء
  disabled boolean not null default false,
  created_by text not null default '',
  device_id text,                         -- الجهاز المرتبط (يُسجَّل عند أول دخول)
  bound_at timestamptz,
  session_token text,
  last_seen timestamptz,
  watching text not null default '',
  watch_minutes int not null default 0,
  fail_count int not null default 0,
  locked_until timestamptz
);
create unique index if not exists fm_members_name_lower on public.fm_members (lower(name));
alter table public.fm_members enable row level security;

-- ===== تحقق المالك (داخلي، لا يُستدعى من التطبيق) =====
create or replace function public._fm_admin_ok(p_secret text)
returns boolean language plpgsql security definer set search_path = public, extensions as $$
declare a public.fm_admin;
begin
  select * into a from public.fm_admin where id = 1;
  if not found then return false; end if;
  if a.locked_until is not null and a.locked_until > now() then return false; end if;
  if p_secret is not null and a.secret_hash = crypt(p_secret, a.secret_hash) then
    update public.fm_admin set fail_count = 0 where id = 1;
    return true;
  end if;
  update public.fm_admin set
    fail_count = case when a.fail_count + 1 >= 5 then 0 else a.fail_count + 1 end,
    locked_until = case when a.fail_count + 1 >= 5 then now() + interval '10 minutes' else a.locked_until end
  where id = 1;
  return false;
end $$;

-- ===== دخول العضو =====
create or replace function public.fm_login(p_name text, p_pass text, p_device text)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare m public.fm_members; tok text;
begin
  if p_device is null or length(p_device) < 8 then
    return jsonb_build_object('ok', false, 'code', 'bad_device');
  end if;
  select * into m from public.fm_members where lower(name) = lower(trim(p_name));
  if not found then return jsonb_build_object('ok', false, 'code', 'not_registered'); end if;
  if m.locked_until is not null and m.locked_until > now() then
    return jsonb_build_object('ok', false, 'code', 'locked');
  end if;
  if p_pass is null or m.pass_hash is distinct from crypt(p_pass, m.pass_hash) then
    update public.fm_members set
      fail_count = case when m.fail_count + 1 >= 5 then 0 else m.fail_count + 1 end,
      locked_until = case when m.fail_count + 1 >= 5 then now() + interval '5 minutes' else m.locked_until end
    where name = m.name;
    return jsonb_build_object('ok', false, 'code', 'wrong_password');
  end if;
  if m.disabled then return jsonb_build_object('ok', false, 'code', 'disabled'); end if;
  if m.expires_at is not null and m.expires_at < now() then
    return jsonb_build_object('ok', false, 'code', 'expired',
      'expires', (extract(epoch from m.expires_at) * 1000)::bigint);
  end if;
  -- الحساب مرتبط بجهاز واحد: أي جهاز آخر يُرفض حتى يعيد المالك ضبط الجهاز
  if m.device_id is not null and m.device_id <> p_device then
    return jsonb_build_object('ok', false, 'code', 'other_device');
  end if;
  tok := encode(gen_random_bytes(24), 'hex');
  update public.fm_members set
    device_id = p_device, bound_at = coalesce(bound_at, now()),
    session_token = tok, fail_count = 0, last_seen = now()
  where name = m.name;
  return jsonb_build_object('ok', true, 'name', m.name, 'token', tok,
    'expires', coalesce((extract(epoch from m.expires_at) * 1000)::bigint, 0));
end $$;

-- ===== نبضة التطبيق (كل دقيقة): تتحقق من الجلسة وتسجّل الحضور =====
create or replace function public.fm_check(
  p_name text, p_device text, p_token text, p_watching text default '', p_playing boolean default false)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare m public.fm_members;
begin
  select * into m from public.fm_members where lower(name) = lower(trim(p_name));
  if not found then return jsonb_build_object('ok', false, 'code', 'gone'); end if;
  if m.session_token is null or p_token is null or m.session_token <> p_token
     or m.device_id is distinct from p_device then
    return jsonb_build_object('ok', false, 'code', 'revoked');
  end if;
  if m.disabled then return jsonb_build_object('ok', false, 'code', 'disabled'); end if;
  if m.expires_at is not null and m.expires_at < now() then
    return jsonb_build_object('ok', false, 'code', 'expired');
  end if;
  update public.fm_members set
    last_seen = now(),
    watching = case when p_playing then left(coalesce(p_watching, ''), 80) else '' end,
    watch_minutes = watch_minutes + case when p_playing then 1 else 0 end
  where name = m.name;
  return jsonb_build_object('ok', true);
end $$;

-- ===== دوال المالك (تتطلب السر) =====
create or replace function public.fm_admin_list(p_secret text)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
begin
  if not public._fm_admin_ok(p_secret) then return jsonb_build_object('ok', false, 'code', 'denied'); end if;
  return jsonb_build_object('ok', true, 'members', coalesce((
    select jsonb_agg(jsonb_build_object(
      'name', name,
      'created', (extract(epoch from created_at) * 1000)::bigint,
      'expires', coalesce((extract(epoch from expires_at) * 1000)::bigint, 0),
      'disabled', disabled,
      'by', created_by,
      'bound', device_id is not null,
      'bound_at', coalesce((extract(epoch from bound_at) * 1000)::bigint, 0),
      'last_seen', coalesce((extract(epoch from last_seen) * 1000)::bigint, 0),
      'watching', watching,
      'minutes', watch_minutes,
      'online', coalesce(last_seen > now() - interval '3 minutes', false)
    ) order by created_at desc) from public.fm_members), '[]'::jsonb));
end $$;

create or replace function public.fm_admin_add(
  p_secret text, p_name text, p_pass text, p_days int, p_by text default '')
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare n text := trim(coalesce(p_name, ''));
begin
  if not public._fm_admin_ok(p_secret) then return jsonb_build_object('ok', false, 'code', 'denied'); end if;
  if n !~ '^[A-Za-z0-9_.-]{3,32}$' then return jsonb_build_object('ok', false, 'code', 'bad_name'); end if;
  if p_pass is null or length(p_pass) < 6 then return jsonb_build_object('ok', false, 'code', 'weak_password'); end if;
  if exists (select 1 from public.fm_members where lower(name) = lower(n)) then
    return jsonb_build_object('ok', false, 'code', 'exists');
  end if;
  insert into public.fm_members (name, pass_hash, expires_at, created_by)
  values (n, crypt(p_pass, gen_salt('bf')),
          case when coalesce(p_days, 0) > 0 then now() + make_interval(days => p_days) else null end,
          left(coalesce(p_by, ''), 32));
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.fm_admin_extend(p_secret text, p_name text, p_days int)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
begin
  if not public._fm_admin_ok(p_secret) then return jsonb_build_object('ok', false, 'code', 'denied'); end if;
  update public.fm_members
    set expires_at = greatest(coalesce(expires_at, now()), now()) + make_interval(days => greatest(p_days, 0)),
        disabled = false
  where lower(name) = lower(p_name);
  if not found then return jsonb_build_object('ok', false, 'code', 'not_found'); end if;
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.fm_admin_disable(p_secret text, p_name text, p_disabled boolean)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
begin
  if not public._fm_admin_ok(p_secret) then return jsonb_build_object('ok', false, 'code', 'denied'); end if;
  update public.fm_members set disabled = coalesce(p_disabled, true) where lower(name) = lower(p_name);
  if not found then return jsonb_build_object('ok', false, 'code', 'not_found'); end if;
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.fm_admin_delete(p_secret text, p_name text)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
begin
  if not public._fm_admin_ok(p_secret) then return jsonb_build_object('ok', false, 'code', 'denied'); end if;
  delete from public.fm_members where lower(name) = lower(p_name);
  if not found then return jsonb_build_object('ok', false, 'code', 'not_found'); end if;
  return jsonb_build_object('ok', true);
end $$;

-- فك ارتباط الحساب بجهازه (لينتقل العضو إلى جهاز جديد). يقطع الجهاز القديم عند نبضته التالية.
create or replace function public.fm_admin_reset_device(p_secret text, p_name text)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
begin
  if not public._fm_admin_ok(p_secret) then return jsonb_build_object('ok', false, 'code', 'denied'); end if;
  update public.fm_members set device_id = null, bound_at = null, session_token = null
  where lower(name) = lower(p_name);
  if not found then return jsonb_build_object('ok', false, 'code', 'not_found'); end if;
  return jsonb_build_object('ok', true);
end $$;

-- ===== الصلاحيات: المفتاح العام (anon) يستدعي هذه الدوال فقط =====
revoke all on function public._fm_admin_ok(text) from public, anon, authenticated;
revoke all on function public.fm_login(text, text, text) from public;
revoke all on function public.fm_check(text, text, text, text, boolean) from public;
revoke all on function public.fm_admin_list(text) from public;
revoke all on function public.fm_admin_add(text, text, text, int, text) from public;
revoke all on function public.fm_admin_extend(text, text, int) from public;
revoke all on function public.fm_admin_disable(text, text, boolean) from public;
revoke all on function public.fm_admin_delete(text, text) from public;
revoke all on function public.fm_admin_reset_device(text, text) from public;

grant execute on function public.fm_login(text, text, text) to anon;
grant execute on function public.fm_check(text, text, text, text, boolean) to anon;
grant execute on function public.fm_admin_list(text) to anon;
grant execute on function public.fm_admin_add(text, text, text, int, text) to anon;
grant execute on function public.fm_admin_extend(text, text, int) to anon;
grant execute on function public.fm_admin_disable(text, text, boolean) to anon;
grant execute on function public.fm_admin_delete(text, text) to anon;
grant execute on function public.fm_admin_reset_device(text, text) to anon;

-- ===== سر المالك: شغّل هذا السطر مرة واحدة بعد تعديل النص بين العلامتين =====
-- (24 حرفاً عشوائياً أو أكثر. لا تضعه في الكود، ولا تعطه لأحد.)
-- insert into public.fm_admin (secret_hash)
-- values (extensions.crypt('ضع-هنا-سراً-طويلاً-عشوائياً', extensions.gen_salt('bf')))
-- on conflict (id) do update set secret_hash = excluded.secret_hash, fail_count = 0, locked_until = null;
