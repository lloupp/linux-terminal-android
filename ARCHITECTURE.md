# Arquitetura 0.3.0

- `MainActivity` apresenta terminal, projetos, transferências, ambiente e painel Pi.
- `TerminalSurface` → Termux `TerminalView` → `TerminalSession`/`TerminalEmulator`
  → JNI `termux.c`: PTY, ANSI, UTF-8, resize, seleção e InputConnection.
- `TerminalService` retém até oito shells por projeto/backend durante o processo;
  a Activity só observa. `WorkspaceCatalog` dá IDs estáveis e histórico recente.
- `AndroidShellBackend` e `LinuxRuntime` implementam `ShellBackend`.
  PRoot/loader são empacotados em nativeLibraryDir; rootfs Alpine fica em filesDir.
- `PtyCommandExecutor` cria sessão dedicada para ferramentas, sem injetar comandos
  no TUI do usuário. Sua saída é transcript limitado, não stream binário byte-exato.
- `WorkspaceTransfers` executa import/export/preparo fora da UI; controla progresso
  e cancelamento. Importação publica staging por rename; exportação monta ZIP privado
  antes de copiar ao destino SAF e tenta remover destino incompleto em falha.
- `PiController` possui lifecycle/writer/tools separados, geração de conexão, jobs
  limitados e JSONL limitado a 1 MiB por registro. Stdout RPC separado de stderr.
  Protocolo Pi 1.0.4 fixo; conclusão por evento, timeout de handshake e cancelamento.
- `pi-android-bridge.mjs` substitui ferramentas upstream; `GatedAgentRuntime`
  recebe argumentos imutáveis e exige decisão `ApprovalBroker` por operação.
- `WorkspaceFiles` restringe caminhos canônicos e grava atomicamente. `EditTool`
  exige trecho antigo único; escrita e shell não são classificados por regex.
- `SecretStore`: Keystore AES-GCM/AAD. `SessionStore`: AtomicFile; Pi mantém JSONL.
  `AgentAudit`: últimas 500 decisões/resultados, sem conteúdo/argumentos/segredos.
- Sessões de terminal não sobrevivem force-stop/reboot. Arquivos e conversa Pi sim.
  Shell/PRoot compartilham UID do app; gate não fornece sandbox de SO. Shell aprovado
  pode acessar outros dados do aplicativo. Não execute código não confiável esperando isolamento.
- Clipboard manual tem callback explícito da view; OSC52/paste solicitados pelo processo
  continuam negados. Colagem manual exige revisão e alerta sobre quebras de linha.

## Licenças e origem

Termux v0.118.3, commit `5b657c6adf4304e5198951ce815fe0205dcac29c`, vendorizado em
terminal-emulator/terminal-view com testes e avisos preservados. GPL-3.0-only e
exceções Apache em TERMUX_LICENSE.md; LICENSE contém GPLv3. PRoot GPLv2+ e talloc
LGPLv3+ são baixados de fontes fixas; distribuição combinada GPLv3. Scripts de build
preservam fontes, disponíveis junto do código. Extensão bridge é própria; Pi é
instalado explicitamente pelo usuário, sem copiar seu código para o APK.

## Limitações de validação

CI/emulador API34 não comprovam Gboard/voz físicos, ARM64/16 KiB ou políticas OEM.
Chamada de modelo paga depende de chave do usuário e não integra testes automáticos.
Runtime 32-bit não disponível apesar de PTY empacotado nas quatro ABIs.
