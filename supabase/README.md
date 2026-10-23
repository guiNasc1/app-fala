# Supabase: SQL e Edge Functions

## SQL (tabelas e policies)

Cada arquivo numerado é uma "migração": rode **na ordem**, uma vez, no **SQL Editor** do painel do Supabase.

1. Abra `https://supabase.com/dashboard/project/<id-do-projeto>/sql/new`
   (o id é o pedaço antes de `.supabase.co` na `supabase.url` do `local.properties`).
   Pelo menu: abra o projeto e procure **SQL Editor** na barra de ícones da esquerda (ícone `>_`).
2. Cole o conteúdo do arquivo e clique em **Run** (ou Ctrl+Enter).
3. Se aparecer o aviso de **"destructive operation"** (por causa de `drop`/`revoke`), clique em **"Run this query"**.
   Fechar o aviso cancela tudo.
4. O esperado é **"Success. No rows returned"**. Qualquer erro desfaz o script inteiro: nada fica pela metade.

| Arquivo | O que cria |
|---|---|
| `001_oficinas.sql` | Tabela `oficinas` (dados da oficina de cada conta), RLS e o gatilho que copia os dados do cadastro |
| `002_documentos.sql` | Tabelas `documentos` e `itens_documento`, RLS, numeração sequencial e a função `emitir_documento` (salva tudo numa transação) |

Regras do projeto:

- Toda tabela nasce com **RLS ligado** e com as policies no mesmo arquivo.
- O app só usa a chave **publishable**. Nada aqui exige a `service_role` ou a senha do banco.
- Mudança em algo já rodado vira um **arquivo novo** (`002_...`), não edição do antigo.

## Manutenção

**Apagar os documentos de teste de uma conta** (os itens vão junto; a numeração recomeça do 1).
Rode no SQL Editor trocando o e-mail. Sem o `where`, apagaria os documentos de todas as contas:

```sql
delete from public.documentos
where user_id = (select id from auth.users where email = 'email-da-conta@exemplo.com');
```

## Edge Function `extrair-item` (a IA da emissão por voz)

Recebe o texto de uma fala e devolve veículo, cliente e itens (nome formal, quantidade, valores em centavos).
Só aceita usuários logados no app. A chave da IA fica nos **secrets**; o app nunca a vê.

### Secrets (Edge Functions › Secrets no painel)

| Nome | Para quê |
|---|---|
| `GEMINI_API_KEY` | Chave do Gemini (Google AI Studio). Usada nos testes, plano gratuito. |
| `IA_PROVEDOR` | Opcional. `gemini` (padrão) ou `claude`. |
| `GEMINI_MODELO` | Opcional. Padrão `gemini-3.5-flash-lite`. |
| `ANTHROPIC_API_KEY` | Chave da Claude API, quando trocar para `IA_PROVEDOR=claude`. |
| `CLAUDE_MODELO` | Opcional. Padrão `claude-haiku-5-5`. |

Para **trocar para o Claude Haiku**: crie `ANTHROPIC_API_KEY` e mude `IA_PROVEDOR` para `claude`. Não precisa
publicar de novo nem mexer no app. (O caminho do Claude passou na checagem de tipos, mas só foi testado de
verdade com o Gemini.)

**No plano gratuito do Gemini, o Google pode usar os textos enviados para melhorar os produtos dele:**
use só dados de teste enquanto estiver nele.

### Publicar (deploy)

Na raiz do repositório, com o CLI logado uma vez (`npx supabase login`):

```bash
npx supabase functions deploy extrair-item --project-ref <id-do-projeto> --use-api
```

`--use-api` empacota no servidor, sem precisar de Docker.

### Checar os tipos antes de publicar

```bash
cd supabase/functions/extrair-item && npx deno check index.ts
```

As versões dos pacotes `npm:` ficam fixas nos imports. O Deno recusa versões publicadas há menos de 24 h
(proteção contra pacote malicioso recém-publicado): ao atualizar, escolha uma versão com alguns dias.
