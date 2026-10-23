# Como o Fala Oficina foi feito

Guia para entender o app por dentro e conseguir fazer algo parecido sozinho.
As comparações são com Java/JSF/Spring, que você já conhece.

---

## 1. A visão geral em uma figura

```
 CELULAR (app Android, Kotlin)                SUPABASE (nuvem)                         IA
 ┌──────────────────────────────┐   HTTPS    ┌───────────────────────────────┐
 │ Telas (Jetpack Compose)      │───────────▶│ Auth: login/cadastro (JWT)    │
 │   ↓ chama                    │            │ Postgres + RLS: oficinas,     │
 │ ViewModels (estado da tela)  │            │   documentos, itens           │
 │   ↓ chama                    │            │ PostgREST: API REST automática│
 │ Repositórios (acesso a dados)│            │   em cima das tabelas         │
 │   ↓ usa                      │            │ Edge Function "extrair-item" ─┼──────▶ Gemini (testes)
 │ supabase-kt (cliente HTTP)   │            │   (TypeScript, roda no Deno)  │        Claude Haiku (futuro)
 │                              │            │ Secrets: chave da IA          │
 │ No próprio aparelho:         │            └───────────────────────────────┘
 │  • voz → texto (Google)      │
 │  • PDF (PdfDocument)         │───────────▶ BrasilAPI (dados do CNPJ, público)
 │  • preferências e rascunho   │
 └──────────────────────────────┘
```

Três ideias sustentam tudo:

1. **O app não tem backend próprio.** No lugar de um Spring Boot, o Supabase dá banco, login e uma API REST
   prontos. O único código de servidor é uma função pequena (a da IA).
2. **A segurança mora no banco (RLS), não no app.** O APK só carrega a chave *publishable*, que é pública.
   Mesmo que alguém a extraia, o Postgres só devolve as linhas do usuário dono do token.
3. **Segredo nunca vai no APK.** A chave da IA fica nos *secrets* do Supabase; o app chama a Edge Function e
   ela chama a IA.

---

## 2. O que foi usado (e o equivalente que você conhece)

### No celular

| Tecnologia | Para quê | Equivalente Java/JSF |
|---|---|---|
| **Kotlin 2.4** | Linguagem do app | Java (roda na mesma JVM/ART, conversa com bibliotecas Java) |
| **Jetpack Compose + Material 3** | Telas declarativas | JSF/PrimeFaces: componentes, mas em código Kotlin em vez de XHTML |
| **ViewModel** | Guarda o estado e as ações de cada tela | Managed bean `@ViewScoped` |
| **Coroutines (`suspend`, `launch`)** | Chamadas de rede sem travar a tela | `CompletableFuture`/`@Async`, escrito como código síncrono |
| **kotlinx.serialization** | JSON ⇄ objetos | Jackson |
| **supabase-kt 3.8 + Ktor/OkHttp** | Cliente do Supabase (Auth, Postgrest, Functions) | Um "Retrofit/RestTemplate" já pronto para o Supabase |
| **SpeechRecognizer** | Fala → texto em pt-BR, no aparelho | (não tem equivalente no JSF) |
| **PdfDocument / PdfRenderer** | Desenhar e mostrar o PDF | Graphics2D / iText |
| **FileProvider** | Compartilhar o PDF com WhatsApp etc. | Um link temporário de download |
| **SharedPreferences** | Preferências e rascunho no aparelho | Um `.properties` que sobrevive ao fechar o app |
| **Gradle (Kotlin DSL) + version catalog** | Build e dependências | Maven (`pom.xml`, `<dependencyManagement>`) |
| **R8** | Encolhe e otimiza o APK de release | ProGuard |
| **JUnit 4** | Testes no PC | O mesmo JUnit que você já usa |

Versões fixas em [`gradle/libs.versions.toml`](../gradle/libs.versions.toml): AGP 9.0.1, Gradle 9.2.1,
Compose BOM 2026.06.01, minSdk 26 (Android 8).

### Na nuvem

| Tecnologia | Para quê | Equivalente |
|---|---|---|
| **Supabase Auth** | Cadastro/login por e-mail e senha; emite JWT | Spring Security + JWT |
| **Postgres** | Tabelas `oficinas`, `documentos`, `itens_documento` | O mesmo Postgres |
| **RLS (Row Level Security)** | Cada usuário só enxerga as próprias linhas | Um `WHERE user_id = :usuarioLogado` que o banco aplica sozinho em toda consulta |
| **PostgREST** | API REST automática sobre as tabelas | Os `@RestController` de CRUD que você escreveria |
| **RPC (função SQL)** | `emitir_documento`: salva documento + itens numa transação | Um método `@Transactional` no service |
| **Edge Function (TypeScript/Deno)** | Chama a IA com a chave secreta | Um endpoint Spring pequeno, com a chave em variável de ambiente |
| **Secrets** | Guardam `GEMINI_API_KEY` etc. | `application.properties` fora do git / variáveis de ambiente |
| **Supabase CLI** | Publicar a função (`functions deploy`) | O deploy do seu `.jar` |

### IA

- **Gemini 3.5 Flash-Lite** (plano gratuito, só para testes) via SDK `@google/genai`.
- **Claude Haiku 5.5** pronto no código (`@anthropic-ai/sdk`), ativado trocando um secret.

---

## 3. Arquitetura do app (MVVM)

```
app/src/main/java/br/com/appfala/
├── MainActivity.kt        porta de entrada: liga tema e escolhe a tela inteira (login, cadastro, principal)
├── data/                  "camada de dados" (Repository/DAO)
│   ├── Supabase.kt        o cliente único (singleton) com URL + chave publishable
│   ├── ContaRepositorio   login, cadastro, sessão, oficina
│   ├── IaRepositorio      chama a Edge Function da IA
│   ├── DocumentosRepositorio  emitir (RPC), histórico, itens, resumo mensal
│   ├── ConsultaCnpj       BrasilAPI
│   └── Preferencias       modo escuro, lembrar de mim, rascunho
├── ui/                    telas (Composables) + ViewModels
│   ├── AppViewModel       sessão, navegação, oficina, tema
│   ├── EmissaoViewModel   o rascunho sendo ditado
│   ├── DocumentosViewModel histórico, PDF aberto, gráficos
│   └── Tela*.kt           cada tela
├── voz/ReconhecedorDeFala SpeechRecognizer
├── pdf/GeradorPdf         desenho do A4
└── util/Formatos          CNPJ, máscaras, dinheiro
```

O caminho de uma ação é sempre o mesmo (o MVVM):

```
Tela (Composable)  →  ViewModel  →  Repositório  →  Supabase
   lê o estado         muda o estado   faz a chamada
```

Exemplo real: tocar em **Entrar** ([`TelasConta.kt`](../app/src/main/java/br/com/appfala/ui/TelasConta.kt))
chama `vm.entrar(...)` no [`AppViewModel`](../app/src/main/java/br/com/appfala/ui/AppViewModel.kt), que chama
`conta.entrar(...)` no [`ContaRepositorio`](../app/src/main/java/br/com/appfala/data/ContaRepositorio.kt), que usa
`auth.signInWith(Email)`. Quando o login muda, o ViewModel (que observa `sessionStatus`) troca a tela.

### Conceitos de Compose que você precisa dominar

- **Estado observável:** `var x by mutableStateOf(...)`. Quando muda, o Compose redesenha só o que lê `x`
  (como um `update="@form"` automático).
- **Recomposição:** a função `@Composable` é chamada de novo sempre que o estado que ela lê muda. Por isso ela
  não pode "guardar" nada em variável comum; usa `remember` (vive enquanto a tela está aberta) ou
  `rememberSaveable` (sobrevive a girar o celular).
- **Efeitos:** `LaunchedEffect` (rodar algo quando a tela aparece, ex.: carregar o histórico),
  `DisposableEffect` (criar/liberar recurso, ex.: o reconhecedor de voz).
- **Navegação:** aqui é simples, por estado: um `enum Tela` (login, cadastro, principal…) e um `enum Aba`
  (Início, Emitir, Histórico, Perfil) — um `when` escolhe o que desenhar. Para apps maiores existe a
  biblioteca Navigation Compose.

---

## 4. Banco e segurança

SQL versionado em [`supabase/`](../supabase/), rodado no SQL Editor:

- [`001_oficinas.sql`](../supabase/001_oficinas.sql): tabela `oficinas` (uma por usuário), **RLS** e um
  **gatilho** que, quando o Supabase cria o usuário, copia os dados da oficina mandados no cadastro. Isso
  funciona mesmo com confirmação de e-mail (quando o usuário ainda não pode gravar nada).
- [`002_documentos.sql`](../supabase/002_documentos.sql): `documentos` e `itens_documento`, RLS, numeração
  sequencial por tipo (gatilho com *advisory lock*) e a função **`emitir_documento`**, que grava tudo numa
  transação e soma o total no banco (não confia no total vindo do app). O documento guarda uma cópia dos
  dados da oficina, para o PDF refeito depois sair igual.

Como uma policy de RLS se lê:

```sql
create policy "documentos: dono le" on public.documentos
    for select to authenticated
    using ((select auth.uid()) = user_id);
```

"Usuários logados podem ler as linhas cujo `user_id` é o id de quem está chamando." `auth.uid()` vem do JWT
que o app manda em toda requisição. Sem policy, a tabela fica fechada.

---

## 5. A integração com a IA, passo a passo

Arquivos: [`voz/ReconhecedorDeFala.kt`](../app/src/main/java/br/com/appfala/voz/ReconhecedorDeFala.kt),
[`ui/EmissaoViewModel.kt`](../app/src/main/java/br/com/appfala/ui/EmissaoViewModel.kt),
[`data/IaRepositorio.kt`](../app/src/main/java/br/com/appfala/data/IaRepositorio.kt) e a função em
[`supabase/functions/extrair-item/`](../supabase/functions/extrair-item/).

```
1. Mecânico fala          "duas bombas de óleo, mil cento e cinco cada"
2. Celular (Google)  →    texto (SpeechRecognizer, pt-BR)          ← de graça, no aparelho
3. App  →  Edge Function  POST {texto, tipo, veiculo} + JWT do usuário
4. Função valida o login  (withSupabase({auth:"user"}))
5. Função monta o pedido  prompt de regras + contexto (orçamento/nota, moto já dita) + a fala
6. Função chama a IA      pedindo JSON num formato fixo (JSON Schema)
7. Função confere o JSON  valores em centavos, unitário x total, item sem preço vira aviso
8. App recebe e aplica    moto/cliente no cartão, itens na lista, total recalculado
```

### Por que cada decisão

- **Voz → texto no celular, só o texto vai para a IA:** é grátis, rápido e manda menos dados para fora.
- **Edge Function no meio:** a chave da IA não pode ir no APK (qualquer um extrai). A função também valida
  o login, então só usuários do app gastam a IA.
- **Saída estruturada (JSON Schema):** em vez de pedir "responda em JSON" e torcer, o pedido manda o
  esquema ([`extracao.ts`](../supabase/functions/extrair-item/extracao.ts), `ESQUEMA`) e o modelo é
  obrigado a seguir. No Gemini é `responseJsonSchema`; no Claude, `output_config.format`.
- **Nunca confiar cegamente no modelo:** `normalizar()` converte reais em centavos, calcula unitário/total
  e descarta item sem descrição ou valor. É a "validação do DTO" do lado do servidor.
- **O prompt é a especificação:** está em `PROMPT`, com as regras (nome formal, "cada" = unitário,
  medidas não são preço, "(par)" só quando dito) e um exemplo. Quando a IA errou ("Cilindros… (par)" com
  quantidade 2), a correção foi uma regra a mais no prompt e um novo deploy, sem mexer no app.
- **Provedor trocável:** o secret `IA_PROVEDOR` escolhe `gemini` ou `claude`; os dois devolvem o mesmo JSON.
- **No app:** um `Mutex` faz as falas serem processadas na ordem em que foram ditas; cada fala aparece como
  "Entendendo…" até voltar; falhas viram aviso com "Tentar de novo".

### Custo (Claude Haiku 5.5, quando trocar)

US$ 0,10 por milhão de tokens de entrada e US$ 0,50 de saída. Cada fala gasta ~1.600 + ~350 tokens:
cerca de US$ 0,0003. 600 falas por mês ≈ US$ 0,20.

---

## 6. O resto do app, resumido

- **PDF** ([`pdf/GeradorPdf.kt`](../app/src/main/java/br/com/appfala/pdf/GeradorPdf.kt)): desenhado à mão
  num `Canvas` A4 (595 × 842 pontos), com quebra de página. Mostrado com `PdfRenderer`, compartilhado via
  `FileProvider`, salvo com o seletor de pasta do Android (`CreateDocument`).
- **Histórico:** lista de `documentos` (o RLS filtra o usuário); tocar busca os itens e refaz o PDF.
- **Início:** busca tipo/total/data dos últimos 6 meses, soma por mês **no fuso do celular** e desenha as
  barras num `Canvas`. As cores foram validadas para daltonismo e contraste nos dois temas.
- **Rascunho que sobrevive:** o ditado é salvo em JSON nas `SharedPreferences` a cada mudança.
- **Release:** R8 ligado (APK de 2 MB), assinatura pela sua chave (`keystore.properties`), instalação por ADB.
  Ver [`INSTALAR.md`](INSTALAR.md).

---

## 7. Como o trabalho foi conduzido

1. **Fases pequenas e entregáveis:** esqueleto → login → voz/IA → PDF/histórico → gráficos → release.
   Cada uma terminava compilando, testada no emulador e com commit.
2. **Testar no aparelho de verdade (emulador) a cada passo,** não só compilar: `adb` para instalar,
   tocar na tela, tirar screenshot e ler o log (`logcat`). O botão **Digitar** existe também para testar
   sem microfone.
3. **Consultar a documentação/tipos em vez de chutar:** quando a API de uma biblioteca era duvidosa,
   a resposta veio das definições de tipo do pacote (ex.: `responseJsonSchema` no `@google/genai`) ou de
   `javap` nas classes da `supabase-kt`.
4. **Checagens automáticas onde dá:** `deno check` na função antes do deploy, testes JUnit nas contas do
   Início, validador de cores no gráfico.

### Problemas reais que apareceram (e o que ensinam)

| Problema | Causa | Lição |
|---|---|---|
| Biblioteca não compilava | `supabase-kt` 3.8 feita com Kotlin 2.4; o projeto estava em 2.2 | Versões de Kotlin e bibliotecas precisam conversar |
| Erro de *foreign key* ao salvar a oficina | A conta foi apagada no painel, mas o token no celular continuava válido | Sessão local pode estar "morta"; confira no servidor em casos estranhos |
| Validação de login na função | O projeto usa chaves JWT novas (ES256) | Use o pacote oficial `@supabase/server` |
| Deno recusou um pacote | Versão publicada há menos de 24 h | Proteção contra pacote malicioso: fixe versões com alguns dias |
| Orçamento das 22h do dia 31 no mês errado | Banco guarda em UTC | Converta para o fuso local antes de agrupar por data |
| SQL "rodou" mas a tabela não existia | O aviso de "destructive operation" foi fechado | Sempre confira o "Success" e teste pela API |
| Xiaomi recusou o APK | "Instalar via USB" desligado | Cada fabricante tem suas travas |

---

## 8. O que estudar para fazer sozinho (em ordem)

1. **Kotlin** (1–2 semanas, com seu Java): null safety (`?`, `?:`, `!!`), `data class`, `object`, lambdas,
   funções de extensão, `when`, `sealed`, coleções (`map`, `filter`, `groupBy`, `sumOf`).
   → [Kotlin Koans](https://play.kotlinlang.org/koans) e [kotlinlang.org/docs](https://kotlinlang.org/docs/home.html).
2. **Coroutines:** `suspend`, `launch`, `withContext(Dispatchers.IO)`, `viewModelScope`, `Flow`/`StateFlow`.
3. **Jetpack Compose:** estado, recomposição, `remember`, layouts (`Column`/`Row`/`Box`/`LazyColumn`),
   `Modifier`, Material 3, efeitos.
   → curso "Android Basics with Compose" em [developer.android.com](https://developer.android.com/courses).
4. **Android em si:** Activity e ciclo de vida, ViewModel, permissões em tempo de execução, Intents,
   `FileProvider`, Logcat, emulador e `adb`.
5. **Gradle Kotlin DSL:** `build.gradle.kts`, version catalog, build types (debug/release), assinatura.
6. **Postgres + Supabase:** SQL, RLS e policies, Auth, a API REST (PostgREST), funções SQL (RPC),
   Edge Functions. → [supabase.com/docs](https://supabase.com/docs).
7. **TypeScript básico** (para Edge Functions): tipos, `async/await`, `import` de pacotes `npm:`.
8. **APIs de LLM:** prompt de sistema, saída estruturada (JSON Schema), tokens e custo, nunca expor a chave.
   → docs da [Claude API](https://platform.claude.com/docs) e do [Gemini](https://ai.google.dev/gemini-api/docs).

### Exercícios no próprio projeto (do mais fácil ao mais difícil)

1. **Mudar um texto ou cor** e ver no emulador ([`Tema.kt`](../app/src/main/java/br/com/appfala/ui/tema/Tema.kt)).
2. **Escrever um teste novo** em [`ResumoMensalTest.kt`](../app/src/test/java/br/com/appfala/ui/ResumoMensalTest.kt)
   (ex.: `formatarQuantidade(1.5)` deve dar `"1,5"`).
3. **Editar um item** antes de gerar (diálogo com descrição/quantidade/valor) no `EmissaoViewModel`.
4. **Apagar documento no Histórico:** a policy de delete já existe; falta o botão, a confirmação e a chamada
   `from("documentos").delete { filter { eq("id", id) } }`.
5. **Mudar o prompt da IA** em `extracao.ts`, rodar `npx deno check index.ts` e publicar.
6. **Foto de perfil:** bucket no Storage + policies (SQL 003), seletor de foto do Android e upload.
7. **Transformar orçamento em nota** depois do serviço feito (nova RPC copiando os itens).
