# Linux Terminal Android

Terminal Android em PTY e fundação para integração futura do Pi Agent.
`applicationId`: **com.linuxterminal.android**; minSdk 24, target/compile 34.

## Disponível neste ciclo

- Terminal ANSI/UTF-8/scrollback com PTY JNI, shell `/system/bin/sh`, resize e controles.
- InputConnection com composição e texto; Gboard/voz aguardam teste físico.
- Serviço foreground retém sessão fora da Activity; arquivos em armazenamento privado.
- Importar snapshot de projeto por SAF; APIs de ferramentas e gate de aprovação.
- Interfaces de agente, metadados de sessão e armazenamento de segredo via Keystore.

**Debian, apt/git/node/python e Pi não estão integrados.** O shell inicial é Android.
Veja [provisionamento](PROVISION.md), [auditoria](docs/AUDIT.md),
[análise Pi](docs/PI_ANDROID_ANALYSIS.md) e [testes físicos](docs/PHYSICAL_TEST_PLAN.md).

## Build

JDK17, SDK34, build-tools34.0.0, NDK26.1.10909125, Gradle8.2.
Executar `gradle lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest`.
CI compila JNI arm64-v8a, armeabi-v7a, x86_64 e x86, publica APK debug e roda testes
reais de PTY/InputConnection em emulador API34. Artefatos ficam em GitHub Actions.
Não instalar APK de outra assinatura esperando preservar dados: atualizações precisam
applicationId igual, assinatura igual e versionCode crescente. CI usa chave debug;
estabilidade de assinatura entre builds precisa estratégia própria antes de uso real.

## Dados

Atualizar preserva arquivos privados; desinstalar/limpar dados remove. Force-stop
encerra processos, mas não arquivos. Não há auto-instalação de agentes nem reset.
O gate de ferramentas não é sandbox para processos com UID do app.

Distribuição combinada GPL-3.0-only; detalhes em [arquitetura](ARCHITECTURE.md).
