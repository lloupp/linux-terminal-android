# Auditoria — 2026-10-04

## Fonte de verdade

- main: 4ebea14285a3bd4428a83e37197edfb44dc742d2.
- Branches remotas encontradas: main e dev.
- PR #1 aberta, não merged: dev → main; HEAD 0199a0721f0caefbad7cc9ad383f0ed15f01d52d.
- 7 commits, 8 arquivos alterados na PR #1; CI Android run 36941642438 falhou.
- Logs job 110634271339: AndroidX presente, `android.useAndroidX` não habilitado.
- Sem AGENTS.md neste repositório no checkout auditado.

## Problemas encontrados

| Área | Evidência anterior | Correção/limite deste ciclo |
|---|---|---|
| main | Activity sem launcher; layout de lista | Usar branch baseada no HEAD dev; preservar main |
| Terminal | AppCompatEditText com append/local echo | TerminalView + TerminalEmulator + TerminalSession + JNI PTY upstream |
| Teclas | Esc/Tab/setas apenas inseridos na edição | Enviar eventos ao terminal e controles à sessão |
| IME | EditText aceita texto mas não envia a shell | InputConnection com composição separada, texto UTF-8 real; teste físico pendente |
| Tema | Activity platform + AppCompatEditText | Surface nativa, sem necessidade de AppCompatEditText |
| Gradle | Sem AndroidX property/wrapper/testes | Propriedade corrigida; build e testes como gates |
| PTY | Apenas planejado | JNI auditável, quatro ABIs |
| Persistência | Nenhuma implementação | Filesystem privado e serviço foreground; processo não sobrevive force-stop |
| Debian | PROVISION.md com promessa sem código | Bloqueio de execução explicitado, sem bootstrap falso |
| Segurança | Sem API de permissões | Runtime gate, defaults negam escrita/execução; não é sandbox de SO |
| CI | Artefato não gerado devido à falha | Lint/unit/assemble + checagem ABI + teste PTY em emulador |
| Licenças | Nenhuma licença | GPL-3.0-only para distribuição combinada; avisos upstream preservados |

## Revisão de código nativo

`PtyProcess` é a camada JNI `com.termux.terminal.JNI`/`termux.c`; não criamos
um segundo transporte. A sessão upstream gerencia leitores/escritores, waitpid,
resize e encerramento. TerminalView deixou de ser final para adaptação IME;
linker/packaging preparados para páginas de 16KiB, ainda sem teste físico. Corrigido pareamento `ReleaseStringUTFChars`: cwd deve ser
liberado com o jstring cwd, não cmd. Na revisão seguinte, a conversão de argumentos,
environment e caminhos foi trocada por UTF-8 padrão: GetStringUTFChars usa modified
UTF-8 e pode corromper caracteres suplementares. O teste PTY inclui emoji no argumento. Testes de emulador upstream preservados.

## Escopo real

Terminal Android e fundação para agente. Debian/Pi não concluídos. Provider é
contrato, não conexão LLM pronta. Ferramentas não são expostas ao modelo/UI antes
de haver frontend de aprovação e integração de provider. Não afirmar Gboard/voz
funcional com base apenas em testes de InputConnection.
