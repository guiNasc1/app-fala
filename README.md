# app-fala

App Android (Kotlin + Jetpack Compose) para uma oficina de motos: orçamentos e notas de serviço
**ditados por voz**, com uma IA que entende cada item e um PDF no final.
A especificação está em [docs/IDEIA.md](docs/IDEIA.md) e as decisões de arquitetura em [CLAUDE.md](CLAUDE.md).

## Como rodar

1. Abra a pasta no **Android Studio** (File › Open) e espere o Gradle sincronizar.
2. Confira o `local.properties` na raiz (ele **não** vai para o git):

   ```properties
   sdk.dir=C\:\\Users\\<você>\\AppData\\Local\\Android\\Sdk
   supabase.url=https://<projeto>.supabase.co
   supabase.publishableKey=sb_publishable_...
   ```

   Só a URL do projeto e a chave **publishable**. Nunca coloque a `service_role`/secret key nem a senha do banco.
3. Rode os scripts de [`supabase/`](supabase/README.md) no SQL Editor do Supabase, na ordem.
4. Rode o app (▶) num emulador ou celular com Android 8.0 (API 26) ou mais novo.

## Estrutura

```
app/src/main/java/br/com/appfala/
├── MainActivity.kt      ponto de entrada; liga o tema e a tela
├── data/                acesso a dados (Supabase, BrasilAPI, preferências do aparelho)
├── ui/                  telas Compose + ViewModel
│   └── tema/            cores e tipografia (claro e escuro)
└── util/                CNPJ e máscaras de campo
supabase/                SQL das tabelas e policies (rodar no SQL Editor do Supabase)
```

## Versões

AGP 9.0.1 e Gradle 9.2.1 (as mesmas do projeto NotaServico), com o Kotlin elevado para **2.4.21**
porque a `supabase-kt` 3.8.0 é compilada com Kotlin 2.4. Tudo fica em `gradle/libs.versions.toml`.
