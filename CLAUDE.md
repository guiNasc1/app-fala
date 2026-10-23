# app-fala

App Android nativo, criado do zero e levado a sério (não é projeto de estudo descartável).
Orçamentos e notas de serviço **ditados por voz** para uma oficina de motos (o pai do usuário, leigo em tecnologia):
uma IA extrai peça, quantidade e valor de cada fala e o app gera o PDF.
**Especificação completa em [docs/IDEIA.md](docs/IDEIA.md)** — leia antes de mexer em telas ou fluxos.

## Quem é o usuário

- Vem de Java + JSF/PrimeFaces; estudando Spring Boot e Angular.
- **Não conhece Kotlin nem desenvolvimento mobile.** Explique as partes novas comparando com o equivalente
  em Java/JSF (ex.: `data class` ≈ POJO com equals/hashCode; Composable ≈ componente de página; ViewModel ≈ managed bean).
- Lê e roda o código no **Android Studio**; Claude escreve e altera os arquivos.
- Converse em português.

## Decisões fixas

- **Só Android.** Sem iOS.
- **Kotlin + Jetpack Compose + Material 3**, arquitetura MVVM (Composable → ViewModel → Repository).
- **Backend = Supabase**, acessado pela biblioteca oficial `supabase-kt` (Postgrest, Auth, etc.).
  - O app usa **apenas a URL do projeto e a chave publishable** (`sb_publishable_...`).
  - **Nunca** conectar direto no Postgres, nem embutir a `service_role`/secret key ou a senha do banco no app.
  - A segurança vem de **RLS** em toda tabela. Todo SQL de tabela nova sai junto com suas policies.
  - O SQL (tabelas, policies) fica versionado em `supabase/` e o usuário roda no SQL Editor do Supabase.
- **IA (extração dos itens ditados):** a chave da API de LLM **nunca** vai no APK. O app chama uma
  **Supabase Edge Function** autenticada (JWT do usuário), e a chave do LLM fica nos secrets do Supabase.
  Fala → texto pode ser feito no aparelho (`SpeechRecognizer`, pt-BR) e só o texto vai para a função.
- URL e chave ficam em `local.properties` (fora do git: `supabase.url`, `supabase.publishableKey`)
  e chegam ao código via `BuildConfig`.

## Ambiente

- Windows, notebook i5 + Iris Xe, 16 GB: emulador é pesado, prefira builds enxutos.
- Android SDK em `C:\Users\guilh\AppData\Local\Android\Sdk`. JDK 21 no JAVA_HOME.
- Referência de versões que já funcionaram nesta máquina (projeto `AndroidStudioProjects\NotaServico`):
  AGP 9.0.1 (Kotlin embutido), plugin Kotlin Compose 2.2.10, Gradle 9.2.1, Compose BOM 2026.06.01, minSdk 26.
- **Neste projeto o Kotlin é 2.4.21** (não 2.2.10): a `supabase-kt` 3.7+ é compilada com Kotlin 2.4.
  O AGP continua 9.0.1; o plugin `org.jetbrains.kotlin.android` declarado com `apply false` no build da raiz
  faz o AGP usar o Kotlin mais novo. Não "corrigir" de volta.
- Emulador `Medium_Phone_API_36.1`: a primeira abertura depois de instalar leva ~7 s (debug); as seguintes ~2-3 s.
  O usuário costuma mexer no emulador junto: antes de tocar na tela via `adb`, tire um screenshot.
  O emulador não recebe áudio do PC por padrão: teste as falas pelo botão **Digitar** (`adb shell input text`,
  sem acentos, espaços como `%s`).

## Convenções do app

- **Cor por tipo de documento, igual em todo o app:** vermelho = orçamento, azul = nota
  (`CoresDocumento` em `ui/tema/Tema.kt`). As cores do gráfico foram validadas para daltonismo e contraste
  nos dois temas; ao mudar, revalide.
- Dinheiro sempre em centavos (`Long`); datas do banco em UTC, convertidas para o fuso do celular na tela.
- Testes unitários (JVM, sem emulador): `./gradlew testDebugUnitTest`.
- Release: R8 ligado (APK ~2 MB); assinatura pela `keystore.properties` (fora do git, a chave é do usuário).
  Sem ela o `packageRelease` falha de propósito. Para testar o release no emulador sem a chave dele:
  `./gradlew assembleRelease -PreleaseComChaveDeDebug`. Nunca pedir nem ler as senhas da chave.
  Guia do usuário em [docs/INSTALAR.md](docs/INSTALAR.md).

## IA da emissão (Edge Function `supabase/functions/extrair-item`)

- Decisão do usuário (2026-10-22): **Gemini gratuito para testes** e, no futuro, **Claude Haiku 5.5**.
  O provedor é escolhido pelo secret `IA_PROVEDOR` (`gemini` padrão / `claude`); a troca não exige deploy.
- Login validado com `@supabase/server` (`withSupabase({ auth: "user" })`): o projeto usa chaves JWT ES256.
- Deploy: `npx supabase functions deploy extrair-item --project-ref <ref> --use-api` (sem Docker; o CLI
  já foi logado pelo usuário). Checagem de tipos: `npx deno check index.ts` dentro da pasta da função.
- Detalhes, secrets e passo a passo em [supabase/README.md](supabase/README.md).
- Repositório: https://github.com/guiNasc1/app-fala (privado), branch `main`. `gh` CLI não instalado.
- No Git Bash, comandos `adb` com caminhos precisam de `MSYS_NO_PATHCONV=1`.
