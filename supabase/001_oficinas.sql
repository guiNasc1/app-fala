-- =====================================================================================
-- 001 - Oficinas: os dados da oficina de cada conta.
-- Rodar uma vez no SQL Editor do Supabase (pode rodar de novo sem quebrar nada).
-- =====================================================================================

-- Uma linha por usuário do Supabase Auth. A chave primária é o próprio id do usuário,
-- então cada conta tem exatamente uma oficina. Apagar o usuário apaga a oficina junto.
create table if not exists public.oficinas (
    user_id        uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    nome_usuario   text not null default '',
    cnpj           text not null,
    razao_social   text not null default '',
    nome_fantasia  text not null default '',
    telefone       text not null default '',
    email          text not null default '',  -- e-mail de contato que sai no PDF (pode ser diferente do login)
    endereco       text not null default '',  -- rua, número e complemento
    bairro         text not null default '',
    cidade         text not null default '',
    uf             text not null default '',
    cep            text not null default '',
    criado_em      timestamptz not null default now(),
    atualizado_em  timestamptz not null default now()
);

-- ------------------------------------------------------------------ RLS
-- Com RLS ligado, a tabela começa fechada: só passa o que uma policy liberar.
-- auth.uid() é o id do usuário dono do token (JWT) que o app manda em cada requisição.
-- O "(select auth.uid())" é a forma recomendada pelo Supabase: calcula uma vez por consulta.
alter table public.oficinas enable row level security;

drop policy if exists "oficina: dono le" on public.oficinas;
create policy "oficina: dono le" on public.oficinas
    for select to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists "oficina: dono cria" on public.oficinas;
create policy "oficina: dono cria" on public.oficinas
    for insert to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists "oficina: dono edita" on public.oficinas;
create policy "oficina: dono edita" on public.oficinas
    for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

-- Sem policy de delete: a oficina só some junto com a conta.
-- Quem não está logado (anon) não enxerga nada.
revoke all on public.oficinas from anon;
grant select, insert, update on public.oficinas to authenticated;

-- ------------------------------------------------------------------ atualizado_em
create or replace function public.tocar_atualizado_em()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.atualizado_em := now();
    return new;
end;
$$;

drop trigger if exists oficinas_atualizado_em on public.oficinas;
create trigger oficinas_atualizado_em
    before update on public.oficinas
    for each row execute function public.tocar_atualizado_em();

-- ------------------------------------------------------------------ cadastro
-- No cadastro o app manda os dados da oficina como "metadados" do usuário. Quando o Supabase
-- cria o usuário em auth.users, este gatilho copia esses dados para public.oficinas.
-- Assim funciona mesmo com a confirmação de e-mail ligada (o usuário ainda não tem login
-- nessa hora, então o app não conseguiria gravar a oficina sozinho por causa do RLS).
-- "security definer" = roda com o dono da função (que pode escrever na tabela), não com o usuário.
create or replace function public.criar_oficina_do_cadastro()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    m jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
begin
    if m ? 'cnpj' then
        insert into public.oficinas (
            user_id, nome_usuario, cnpj, razao_social, nome_fantasia, telefone, email,
            endereco, bairro, cidade, uf, cep
        ) values (
            new.id,
            coalesce(m ->> 'nome_usuario', ''),
            m ->> 'cnpj',
            coalesce(m ->> 'razao_social', ''),
            coalesce(m ->> 'nome_fantasia', ''),
            coalesce(m ->> 'telefone', ''),
            coalesce(m ->> 'email', ''),
            coalesce(m ->> 'endereco', ''),
            coalesce(m ->> 'bairro', ''),
            coalesce(m ->> 'cidade', ''),
            coalesce(m ->> 'uf', ''),
            coalesce(m ->> 'cep', '')
        )
        on conflict (user_id) do nothing;
    end if;
    return new;
end;
$$;

-- Função de gatilho não deve ser chamável pela API.
revoke execute on function public.criar_oficina_do_cadastro() from public, anon, authenticated;

drop trigger if exists ao_criar_usuario on auth.users;
create trigger ao_criar_usuario
    after insert on auth.users
    for each row execute function public.criar_oficina_do_cadastro();
