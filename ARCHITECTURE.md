# Arquitetura implementada

- `TerminalSurface` → Termux `TerminalView` (render/seleção/InputConnection).
- `TerminalService` → `TerminalSession` → `TerminalEmulator` → JNI `termux.c` PTY.
- `ShellBackend` → `/system/bin/sh`; `PtyCommandExecutor` reutiliza backend/PTY em
  sessão própria, sem injetar comandos num TUI do usuário.
- PTY tem stdin e saída interativa; stdout/stderr são combinados pelo terminal.
- `agent/runtime`, `sessions`, `providers`, `tools`, `permissions`, `persistence`
  independentes da UI do terminal. Não há loop LLM/Pi conectado ainda.
- `FileTool`, `WorkspaceTool`, `ClipboardTool`, `ShareTool`, `ShellTool` registráveis
  em `GatedAgentRuntime`. Toda chamada atravessa gate; default somente leitura.
- Shell arbitrário exige DESTRUCTIVE porque EXECUTE pode embutir remoção/rede.
  READ/WRITE/EXECUTE/DESTRUCTIVE são níveis explícitos, não análise de regex.
- `SessionStore`: metadados versionados/AtomicFile; `SecretStore`: Keystore AES-GCM
  com AAD por provider. Nunca colocar chaves em prompts/workspace/logs.
- SAF importa snapshot privado limitado (2000 documentos, 64 MiB, profundidade 20),
  sem overwrite ou write-back. Não é mount POSIX; `.git` depende do que provider expõe.
- Processos do shell têm UID do app e não estão isolados de seus dados privados.
  Canonical path containment protege FileTool, não oferece sandbox para shell.
- Clipboard OSC52 e paste solicitado pelo processo negados; paste manual tem confirmação.
- Serviço specialUse mantém sessão fora da Activity; sujeito às políticas do Android/OEM.
- Credenciais não têm formulário/provider neste ciclo; armazenamento apenas preparado.

## Licenças e origem

Bibliotecas vendorizadas de termux/termux-app v0.118.3,
commit 5b657c6adf4304e5198951ce815fe0205dcac29c:
`terminal-emulator/src` e `terminal-view/src`, incluindo testes. Sem dependência
JitPack dinâmica. `TERMUX_LICENSE.md` registra GPLv3-only e exceções Apache 2.0.
`LICENSE` contém GPLv3; avisos existentes preservados. Build Gradle/manifest
adaptados para AGP8, JNI cwd corrigido, alinhamento ELF 16KiB e TerminalView
permitindo extensão para configuração IME. Nenhum código Pi copiado.

## Próximo ciclo

Validar Debian em Android moderno, completar frontend de aprovação, execução
assíncrona/cancelamento por job, diff/git sobre Linux real e integração Pi RPC com
pipes separados. Não apresentar scaffolding como agente funcional.
