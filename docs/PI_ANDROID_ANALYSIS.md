# Pi no Android — auditoria de fonte, 2026-10-04

Fonte: clone de `https://github.com/badlogic/pi-mono.git`, que atualmente identifica
`earendil-works/pi` nos manifests. Commit analisado: `b2b5c42f6138b73ec4b2f49ec0ca468800f88586`.
Não foi instalado nem executado neste aplicativo. Não há evidência de Pi nativo neste APK.

## Evidências verificadas

- TypeScript/ES modules; pacote `@earendil-works/pi-coding-agent` 1.0.2 no checkout.
- Node >=22.19.0 nos manifests; Kotlin/JVM não executa estes módulos diretamente.
- Licença MIT no repositório e manifests. Não copiamos código Pi neste ciclo.
- `pi-ai`: providers e streams; `pi-agent-core`: loop/estado/tool calling;
  coding-agent: sessões, ferramentas, compactação, configuração e extensões.
- Ferramentas padrão read/bash/edit/write; outras ferramentas/extensões são versionadas.
- Persistência de sessões JSONL com árvore de entradas e contexto reconstruído;
  compactação tem regras próprias, não é apenas truncar histórico.
- RPC: processo de longa duração, comandos/respostas/eventos JSONL stdin/stdout,
  stderr separado, IDs correlacionados; não confundir resposta de prompt com conclusão.
- O README atual declara ausência de uma barreira de permissões embutida.
  `project_trust` regula recursos de projeto, não limita filesystem/processos.
- Dependências atuais incluem cross-spawn, undici, proper-lockfile, photon-node/WASM,
  quickjs-wasi e pacotes Pi/chord/MCP/codemode. Não presumir compatibilidade transitive.
- Documentação oficial `docs/termux.md` descreve execução em Termux e integração
  clipboard via Termux:API. Isso é evidência upstream, não teste deste applicationId.

## Classificação

| Componente | Classificação | Condição/evidência |
|---|---|---|
| Esquemas e framing RPC JSONL | REUTILIZÁVEL DIRETAMENTE | Transportar records oficiais sem inventar protocolo |
| Pi CLI completo sobre Node em Debian | AINDA NÃO VALIDADO | Falta Debian e Node >=22.19 neste APK |
| pi-agent-core | ADAPTÁVEL | Reutilizar JS com runtime compatível; adaptar lifecycle e tool registry |
| pi-ai/providers | ADAPTÁVEL | JS + rede/TLS; integrar segredo fornecido pelo Keystore sem logs |
| Gerenciamento de contexto/compactação | ADAPTÁVEL | Preferir código upstream em JS; não copiar heurísticas para Kotlin |
| SessionManager/JSONL | ADAPTÁVEL | Filesystem privado, locking e shutdown precisam validação |
| TUI | ADAPTÁVEL | Emulador ANSI existe; executar TUI real ainda depende de Node |
| Ferramentas POSIX read/write/edit/bash | ADAPTÁVEL | Backend aprovado e workspace; não expor indiscriminadamente UID do app |
| Clipboard e compartilhar Android | PRECISA SER REIMPLEMENTADO | Ponte Kotlin com aprovação explícita; Termux:API usa outro applicationId |
| SAF | PRECISA SER REIMPLEMENTADO | URIs de documentos não são caminhos POSIX |
| Controles Android de permissão | PRECISA SER REIMPLEMENTADO | Desativar ferramentas builtin sem gate e impedir caminhos alternativos |
| Binário desktop Bun/Linux pronto | INCOMPATÍVEL COM ANDROID | Não é executável Android/Bionic validado; não embutir como se fosse |
| photon-node/quickjs-wasi e dependências nativas | AINDA NÃO VALIDADO | ABI, loaders, WASM e execução devem ser testados, não presumidos |
| OAuth/provider completo | AINDA NÃO VALIDADO | Redirects/intents/armazenamento e rede dependem de integração real |

## Menor integração proposta

1. Resolver a execução Linux/Node no target 34 com evidência Android; validar Node,
   TLS e Git. Alternativa futura: Node compilado e empacotado no APK por ABI, com
   manutenção de segurança própria. Não baixar executáveis para filesDir e chamar exec.
2. Fixar uma release Pi e lockfile. Validar `pi --version` e uma tarefa simples no
   telefone. Não instalar automaticamente Codex/Claude/Pi durante provisionamento.
3. Iniciar Pi em RPC com **pipes**, não pelo PTY interativo. PTY altera eco/CRLF e
   mistura stderr; `PtyCommandExecutor` serve comandos humanos, não framing RPC.
4. Reusar loop/providers/contexto/sessões upstream. Trocar as ferramentas por uma
   extensão auditada que chama a ponte Android e `PermissionGate` para cada chamada.
5. Negar ferramentas builtin e extensões arbitrárias que contornem o gate. Gate Kotlin
   sozinho não cria sandbox: Pi e processos com o UID do app podem acessar seus dados.
6. Primeiro fluxo: abrir workspace privado → ler arquivo → aprovar escrita → diff →
   aprovar comando de teste → resultado → reabrir sessão. Sem TUI exigida no início.

## Fontes fixadas

- [packages/coding-agent/package.json](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/packages/coding-agent/package.json)
- [packages/agent/package.json](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/packages/agent/package.json)
- [packages/ai/package.json](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/packages/ai/package.json)
- [packages/coding-agent/docs/rpc.md](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/packages/coding-agent/docs/rpc.md)
- [packages/coding-agent/docs/sdk.md](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/packages/coding-agent/docs/sdk.md)
- [packages/coding-agent/docs/session-format.md](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/packages/coding-agent/docs/session-format.md)
- [packages/coding-agent/docs/compaction.md](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/packages/coding-agent/docs/compaction.md)
- [packages/coding-agent/docs/security.md](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/packages/coding-agent/docs/security.md)
- [packages/coding-agent/docs/termux.md](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/packages/coding-agent/docs/termux.md)
- [LICENSE](https://github.com/earendil-works/pi/blob/b2b5c42f6138b73ec4b2f49ec0ca468800f88586/LICENSE)
