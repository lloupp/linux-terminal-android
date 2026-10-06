# Linux Terminal Android 0.3.0

Terminal Android com PTY real, projetos privados e integração experimental Pi RPC.
O shell inicial é `/system/bin/sh`. Linux Alpine é preparado explicitamente pelo usuário.
minSdk 24, target/compile 34; Preview: **com.linuxterminal.android.preview**.

## Fluxos disponíveis

- Terminal ANSI/UTF-8/scrollback com seleção/cópia manual, colagem confirmada,
  Ctrl/Alt visíveis, teclas especiais e fonte persistida/pinch.
- Serviço foreground mantém até oito sessões por projeto/ambiente. Trocar projeto
  preserva shells existentes. Após `exit`, **Nova sessão** abre outro shell.
- **Projetos → Importar pasta** cria snapshot privado via SAF, com progresso,
  cancelamento e limites. **Mais → Exportar projeto em ZIP** devolve os arquivos.
  Não existe sincronização automática com a pasta original.
- **Mais → Rascunho / ditado** permite revisar texto antes de inseri-lo no terminal.
  O microfone depende do teclado instalado; Gboard/voz exigem teste físico.
- **Mais → Ambiente e versões** identifica ABI, runtime e versões instaladas.
- **Mais → Preparar / abrir Linux** baixa Alpine 3.23.0 oficial com SHA-256 fixo,
  staging e prova de execução antes de publicar o runtime. ARM64 e x86_64.
- **Mais → Pi Agent** conecta Pi 1.0.4 via JSONL/RPC, configura modelo/chave
  com Keystore, permite cancelar, registra sessões e histórico de operações.
  Cada ferramenta Android exige aprovação, inclusive leitura; nenhuma aprovação
  é implícita. Não há chamada paga sem modelo/chave fornecidos pelo usuário.

Veja [provisionamento](PROVISION.md), [arquitetura](ARCHITECTURE.md),
[entrega das 12 oportunidades](docs/IMPLEMENTATION_0.3.0.md),
[auditoria original](docs/AUDIT.md) e [testes físicos](docs/PHYSICAL_TEST_PLAN.md).

## Build e verificação

JDK17, SDK34, build-tools34.0.0, NDK26.1.10909125, Gradle8.2:

```sh
export ANDROID_SDK_ROOT=/caminho/android-sdk
bash scripts/build-proot.sh
bash scripts/fetch-runtime-test.sh
gradle lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Os scripts verificam hashes dos fontes PRoot/talloc e da imagem de teste Alpine.
CI compila quatro ABIs de PTY/PRoot/loader; runtime Linux somente ARM64/x86_64.
Testes Android exercitam PTY, InputConnection, teclado, projetos e rootfs real.
A prova de Node/Git/Pi usa rede, instala pacotes só no ambiente de teste e não
chama modelos pagos. Testes do emulador não substituem Gboard/voz, OEM ou ARM64.

## Assinatura e atualização

Preview 0.3.0 usa ID separado: pode coexistir com o APK antigo 0.2.1 e não acessa
seus dados. Exporte os projetos antes de qualquer desinstalação. Não há migração
silenciosa entre IDs. APKs de CI usam debug padrão se não houver segredo privado.
Para atualizações Preview estáveis, forneça fora de Git:

- `ANDROID_PREVIEW_KEYSTORE`: caminho absoluto do keystore privado;
- `ANDROID_SIGNING_STORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS`,
  `ANDROID_SIGNING_KEY_PASSWORD`.

Na CI, configure os secrets de mesmo nome (keystore como base64 no secret
`ANDROID_PREVIEW_KEYSTORE_BASE64`). Chaves privadas nunca pertencem ao Git.
Atualizações preservam dados somente com mesmo ID, assinatura e versionCode crescente.

## Dados e limites

Force-stop/reboot encerra processos; arquivos e sessões Pi persistem. Desinstalar
ou limpar dados remove arquivos e chaves. Exporte antes. ZIP é uma cópia dos
arquivos durante a transferência: pause processos que estejam modificando o projeto.
Importação/exportação limitam 2.000 documentos, 64 MiB e profundidade 20;
exportação rejeita symlinks e arquivos especiais, sem omissão silenciosa.
PRoot e aprovação não isolam processos com UID do app. Preview é depurável;
credenciais de produção exigem uma distribuição release revisada.

Distribuição combinada GPL-3.0-only; avisos em [arquitetura](ARCHITECTURE.md).
