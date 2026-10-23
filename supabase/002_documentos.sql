-- =====================================================================================
-- 002 - Documentos emitidos (orçamentos e notas de serviço) e seus itens.
-- Rodar uma vez no SQL Editor do Supabase, depois do 001 (pode rodar de novo sem quebrar nada).
-- =====================================================================================

create table if not exists public.documentos (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
    tipo            text not null check (tipo in ('ORCAMENTO', 'NOTA')),
    numero          integer not null,                       -- preenchido pelo gatilho abaixo
    veiculo         jsonb not null default '{}'::jsonb,     -- modelo, ano, placa, cor, quilometragem
    cliente         jsonb not null default '{}'::jsonb,     -- nome, telefone
    -- Cópia dos dados da oficina no dia da emissão: o PDF refeito depois sai igual ao original,
    -- mesmo que a oficina mude de endereço ou telefone.
    oficina         jsonb not null default '{}'::jsonb,
    total_centavos  bigint not null default 0 check (total_centavos >= 0),
    criado_em       timestamptz not null default now(),
    unique (user_id, tipo, numero)
);

create index if not exists documentos_usuario_data on public.documentos (user_id, criado_em desc);

create table if not exists public.itens_documento (
    id                       uuid primary key default gen_random_uuid(),
    documento_id             uuid not null references public.documentos (id) on delete cascade,
    user_id                  uuid not null default auth.uid() references auth.users (id) on delete cascade,
    ordem                    integer not null,
    descricao                text not null,
    tipo                     text not null default 'PECA' check (tipo in ('PECA', 'SERVICO')),
    quantidade               numeric(12, 3) not null check (quantidade > 0),
    valor_unitario_centavos  bigint not null check (valor_unitario_centavos >= 0),
    valor_total_centavos     bigint not null check (valor_total_centavos >= 0),
    fala                     text not null default ''      -- o que foi dito, para conferência
);

create index if not exists itens_documento_documento on public.itens_documento (documento_id, ordem);

-- ------------------------------------------------------------------ RLS
-- Cada usuário só vê, cria e apaga os próprios documentos. Não há policy de update:
-- documento emitido não muda (para corrigir, emite-se outro).
alter table public.documentos enable row level security;
alter table public.itens_documento enable row level security;

drop policy if exists "documentos: dono le" on public.documentos;
create policy "documentos: dono le" on public.documentos
    for select to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists "documentos: dono cria" on public.documentos;
create policy "documentos: dono cria" on public.documentos
    for insert to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists "documentos: dono apaga" on public.documentos;
create policy "documentos: dono apaga" on public.documentos
    for delete to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists "itens: dono le" on public.itens_documento;
create policy "itens: dono le" on public.itens_documento
    for select to authenticated
    using ((select auth.uid()) = user_id);

-- Só pode pôr item em documento que também é dele.
drop policy if exists "itens: dono cria" on public.itens_documento;
create policy "itens: dono cria" on public.itens_documento
    for insert to authenticated
    with check (
        (select auth.uid()) = user_id
        and exists (
            select 1 from public.documentos d
            where d.id = documento_id and d.user_id = (select auth.uid())
        )
    );

drop policy if exists "itens: dono apaga" on public.itens_documento;
create policy "itens: dono apaga" on public.itens_documento
    for delete to authenticated
    using ((select auth.uid()) = user_id);

revoke all on public.documentos, public.itens_documento from anon;
grant select, insert, delete on public.documentos, public.itens_documento to authenticated;

-- ------------------------------------------------------------------ numeração
-- Número sequencial por oficina e por tipo: Orçamento nº 1, 2, 3... e Nota nº 1, 2, 3...
-- O advisory lock evita dois documentos com o mesmo número se forem salvos ao mesmo tempo.
create or replace function public.definir_numero_documento()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    perform pg_advisory_xact_lock(hashtext(new.user_id::text || ':' || new.tipo));
    select coalesce(max(d.numero), 0) + 1 into new.numero
      from public.documentos d
     where d.user_id = new.user_id and d.tipo = new.tipo;
    return new;
end;
$$;

drop trigger if exists documentos_numero on public.documentos;
create trigger documentos_numero
    before insert on public.documentos
    for each row execute function public.definir_numero_documento();

-- ------------------------------------------------------------------ emissão
-- Salva o documento e todos os itens numa transação só: ou grava tudo, ou nada.
-- "security invoker" = roda com as permissões de quem chamou, então o RLS acima vale normalmente.
-- O total é somado aqui no banco, a partir dos itens (não confia no total mandado pelo app).
create or replace function public.emitir_documento(
    p_tipo text,
    p_veiculo jsonb,
    p_cliente jsonb,
    p_oficina jsonb,
    p_itens jsonb
)
returns public.documentos
language plpgsql
security invoker
set search_path = ''
as $$
declare
    v_doc public.documentos;
    v_item jsonb;
    v_ordem integer := 0;
    v_total bigint;
begin
    -- Duas checagens separadas: num OR o Postgres não garante a ordem de avaliação.
    if p_itens is null or jsonb_typeof(p_itens) <> 'array' then
        raise exception 'O documento precisa de pelo menos um item.';
    end if;
    if jsonb_array_length(p_itens) = 0 then
        raise exception 'O documento precisa de pelo menos um item.';
    end if;

    select coalesce(sum((i ->> 'valor_total_centavos')::bigint), 0) into v_total
      from jsonb_array_elements(p_itens) as i;

    insert into public.documentos (tipo, veiculo, cliente, oficina, total_centavos)
    values (
        p_tipo,
        coalesce(p_veiculo, '{}'::jsonb),
        coalesce(p_cliente, '{}'::jsonb),
        coalesce(p_oficina, '{}'::jsonb),
        v_total
    )
    returning * into v_doc;

    for v_item in select * from jsonb_array_elements(p_itens) loop
        v_ordem := v_ordem + 1;
        insert into public.itens_documento (
            documento_id, ordem, descricao, tipo, quantidade,
            valor_unitario_centavos, valor_total_centavos, fala
        ) values (
            v_doc.id,
            v_ordem,
            v_item ->> 'descricao',
            coalesce(v_item ->> 'tipo', 'PECA'),
            (v_item ->> 'quantidade')::numeric,
            (v_item ->> 'valor_unitario_centavos')::bigint,
            (v_item ->> 'valor_total_centavos')::bigint,
            coalesce(v_item ->> 'fala', '')
        );
    end loop;

    return v_doc;
end;
$$;

revoke execute on function public.emitir_documento(text, jsonb, jsonb, jsonb, jsonb) from public, anon;
grant execute on function public.emitir_documento(text, jsonb, jsonb, jsonb, jsonb) to authenticated;
