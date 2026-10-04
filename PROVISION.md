# Linux persistente — estado e bloqueio

**Debian NÃO provisionado neste ciclo.** O APK abre `/system/bin/sh` em PTY.
O target permanece 34; não o reduzimos para esconder restrições de execução.

Android 10+ bloqueia `execve()` de código no diretório gravável do app para apps
com target >=29. PRoot não é uma autorização para ignorar SELinux/W^X. Empacotar
apenas PRoot como biblioteca executável não demonstra que o loader glibc e os
binários apt instalados em rootfs gravável possam ser executados/mapeados.

Fonte primária:
https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission
https://github.com/termux/termux-packages/wiki/Termux-and-Android-10
https://github.com/termux/proot

## Estratégia atual

- JNI PTY compilado dentro do APK por ABI; shell fornecido pelo Android.
- `filesDir/home`, `filesDir/workspaces` persistentes; nenhum reset no startup.
- Futuro rootfs em `filesDir/linux`, separado de workspaces/sessões/segredos.
- Atualização de APK não remove dados; desinstalação/limpar dados remove.
- Serviço retém shell enquanto o processo existe. Force-stop/reboot não restaura
  processos; abrir de novo cria sessão nova sobre os mesmos arquivos.

## Bloqueio e menor experimento que desbloqueia

1. PRoot e loader para target 34: não há binários/pipeline/prova no repositório.
2. Evidência: PR #1 não contém PRoot, rootfs, downloader, hash ou chamadas de exec;
   restrição oficial acima impede tratar filesDir como destino executável.
3. Impacto: apt/git/curl/wget/ssh/python/node/npm Debian indisponíveis no APK.
4. Próxima ação de engenharia: construir PRoot+loader no APK por ABI e testar
   ptrace/exec/mmap do rootfs em Android 34 e dispositivo recente. Se inviável,
   avaliar runtime userspace emulado com loader próprio; não mudar target de forma
   silenciosa. Não pedir ao usuário para resolver uma restrição de build.

Antes de implementar importação de rootfs: fonte oficial fixa, SHA-256, assinatura
quando disponível, checagem de ABI/espaço, staging+rename atômico, recusa de
path traversal/symlinks escapando, rollback não destrutivo e recovery idempotente.
Não extrair tar arbitrário em diretório de dados do usuário.

## Gate futuro de Debian

Validar em PTY real `uname -m`, `bash --version`, `apt --version`, `git --version`,
`curl --version`, `wget --version`, `ssh -V`, `python3 --version`, `node --version`
e `npm --version`; resolução DNS/TLS; escrita e atualização do APK. Node precisa
satisfazer a versão exigida pelo Pi, não apenas a versão padrão do Debian.
