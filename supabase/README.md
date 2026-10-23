# SQL do Supabase

Cada arquivo numerado é uma "migração": rode **na ordem**, uma vez, no **SQL Editor** do painel do Supabase.

## Como rodar

1. Abra `https://supabase.com/dashboard/project/<id-do-projeto>/sql/new`
   (o id é o pedaço antes de `.supabase.co` na `supabase.url` do `local.properties`).
   Pelo menu: abra o projeto e procure **SQL Editor** na barra de ícones da esquerda (ícone `>_`).
2. Cole o conteúdo do arquivo e clique em **Run** (ou Ctrl+Enter).
3. Se aparecer o aviso de **"destructive operation"** (por causa de `drop`/`revoke`), clique em **"Run this query"**.
   Fechar o aviso cancela tudo.
4. O esperado é **"Success. No rows returned"**. Qualquer erro desfaz o script inteiro: nada fica pela metade.

## Migrações

| Arquivo | O que cria |
|---|---|
| `001_oficinas.sql` | Tabela `oficinas` (dados da oficina de cada conta), RLS e o gatilho que copia os dados do cadastro |

## Regras do projeto

- Toda tabela nasce com **RLS ligado** e com as policies no mesmo arquivo.
- O app só usa a chave **publishable**. Nada aqui exige a `service_role` ou a senha do banco.
- Mudança em algo já rodado vira um **arquivo novo** (`002_...`), não edição do antigo.
