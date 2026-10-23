# Instalar o Fala Oficina no celular

Passo a passo para gerar a versão **release** (a de uso real), assinada com a sua chave, e instalar
no celular do seu pai. Você faz isto uma vez; nas atualizações, só repete os passos 4 e 5.

## 1. Gerar a chave de assinatura (uma vez só, para sempre)

Todo app Android é assinado. O celular só aceita uma **atualização** se ela vier assinada com
**a mesma chave** da versão instalada. Por isso a chave é única e precisa ser guardada com cuidado.

No PowerShell (fora da pasta do projeto):

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\chaves"
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore "$env:USERPROFILE\chaves\fala-oficina.jks" -alias fala-oficina -keyalg RSA -keysize 2048 -validity 10000
```

Ele pergunta:

- **Senha da keystore**: crie uma senha forte e anote no seu gerenciador de senhas. Não mande para ninguém (nem no chat).
- Nome, unidade, organização, cidade, estado e país: pode responder com os dados da oficina (país: `BR`).
- No fim, confirme com `sim` (ou `yes`).

`-validity 10000` = válida por uns 27 anos.

## 2. Guardar a chave

- O arquivo `fala-oficina.jks` fica **fora** da pasta do projeto (ex.: `C:\Users\<você>\chaves`).
  Ele nunca vai para o git (o `.gitignore` já bloqueia `*.jks`).
- Faça **duas cópias de segurança** em lugares diferentes: um pen drive guardado e um drive na
  nuvem (de preferência dentro de um arquivo compactado com senha).
- A senha vai para o gerenciador de senhas, junto com o nome do alias (`fala-oficina`).

**Se perder a chave ou a senha:** não dá mais para atualizar o app instalado. Seria preciso
desinstalar e instalar de novo. Os orçamentos e notas não se perdem (estão no Supabase), mas o
login e as preferências do celular sim.

## 3. Contar para o Gradle onde está a chave

Crie o arquivo `keystore.properties` na **raiz do projeto** (ao lado do `local.properties`).
Ele também nunca vai para o git:

```properties
storeFile=C:/Users/<você>/chaves/fala-oficina.jks
storePassword=<a senha da keystore>
keyAlias=fala-oficina
keyPassword=<a mesma senha>
```

Use barras `/` no caminho (não `\`). A senha da chave é a mesma da keystore (padrão do `keytool`
atual).

> As senhas ficam em texto puro nesse arquivo, só no seu PC. Se preferir não deixar arquivo
> nenhum: no Android Studio, **Build › Generate Signed App Bundle or APK › APK**, escolha a chave e
> digite as senhas na janela (variante `release`).

## 4. Gerar o APK

Na raiz do projeto:

```powershell
.\gradlew assembleRelease
```

O arquivo sai em `app\build\outputs\apk\release\app-release.apk` (uns 2 MB).
Sem o `keystore.properties`, o build para com a mensagem "Falta a chave de assinatura" — de propósito,
para nunca sair um APK assinado com a chave errada.

## 5. Instalar no celular

Desde 30/09/2026 o Google exige, no Brasil, que apps venham de **desenvolvedor registrado**. O app
ainda não está registrado, então o caminho mais simples e garantido é **pelo cabo USB (ADB)**, que
continua liberado.

### Caminho recomendado: cabo USB

No celular do seu pai (uma vez):

1. **Configurações › Sobre o telefone** e toque **7 vezes** em **Número da versão** (aparece
   "Você agora é um desenvolvedor"). Em alguns aparelhos fica em *Informações do software*.
2. **Configurações › Sistema › Opções do desenvolvedor** e ligue **Depuração USB**.
3. Ligue o cabo no PC e, no celular, toque em **Permitir** na pergunta "Permitir depuração USB?".

No PC:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk
```

O primeiro comando deve listar o celular como `device`. O segundo responde `Success`.
Depois pode desligar a Depuração USB de novo.

### Alternativa: mandar o APK (WhatsApp, Drive)

Abrir o `.apk` no celular também funciona em muitos aparelhos, mas pode ser **bloqueado** como
"app de desenvolvedor não verificado". Se acontecer, há duas saídas:

- **Opções do desenvolvedor › permitir apps de desenvolvedores não verificados**: tem uma espera
  única de 24 horas e mostra avisos na instalação.
- **Registrar o app de graça** no Android Developer Console, com uma conta de "distribuição limitada"
  (até 20 aparelhos, sem taxa nem documento). Depois disso a instalação pelo arquivo volta a ser
  normal. Detalhes no [anúncio do Google](https://android-developers.googleblog.com/2026/06/android-developer-verification.html).

O Play Protect pode avisar "app desconhecido": escolha **Instalar mesmo assim**.

## 6. Primeiro uso no celular do seu pai

1. Abrir o app, **Criar uma conta** com o e-mail dele (ou entrar na sua) e deixar **Lembrar de mim** marcado.
2. Na primeira vez que tocar no microfone, permitir o acesso ao áudio.
3. Fazer um orçamento de teste falando de verdade, para ver o reconhecimento de voz no aparelho.

Antes do uso real, confira no Supabase:

- **Confirmação de e-mail**: se estiver ligada, ele precisa clicar no link do e-mail antes de entrar.
- **Documentos de teste**: apague os de teste da sua conta (SQL no README de `supabase/`).
- **IA**: no plano gratuito do Gemini o Google pode usar os textos; com clientes reais, troque para o
  Claude Haiku (secrets `ANTHROPIC_API_KEY` e `IA_PROVEDOR=claude`, ver `supabase/README.md`).

## Atualizar depois

1. Em `app/build.gradle.kts`, aumente `versionCode` em 1 (e ajuste o `versionName`, ex.: `1.1`).
2. Gere o APK de novo (passo 4) — **com a mesma chave**.
3. Instale por cima (passo 5, `install -r`). Login e preferências continuam.
