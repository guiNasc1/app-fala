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
- Repositório: https://github.com/guiNasc1/app-fala (privado), branch `main`. `gh` CLI não instalado.
- No Git Bash, comandos `adb` com caminhos precisam de `MSYS_NO_PATHCONV=1`.
