# Segurança e limites

- `ReadOnlyGate` permite READ não sensível; WRITE/EXECUTE/DESTRUCTIVE negados.
- Clipboard READ também exige aprovação por ser conteúdo externo/sensível.
- PermissionRequest inclui sessão, operação, recurso e cópia imutável dos parâmetros:
  o futuro frontend deve mostrar a operação exata, sem aprovar alterações posteriores.
- ShellTool usa DESTRUCTIVE sempre para comando arbitrário. Não deduzir segurança
  de prefixos/regex. Processos têm UID do app: PermissionGate não é sandbox de SO.
- FileTool restringe caminho canônico, rejeita symlink de escape, limita tamanho e
  substitui via arquivo temporário+fsync+rename. Confinamento não resiste a processo
  concorrente malicioso modificando symlinks: não misturar shell não confiável com
  expectativas de isolamento, nem expor segredos ao workspace.
- SessionStore guarda só metadados e versão em AtomicFile. Sem histórico/prompt/log
  contendo credenciais. SecretStore usa AES-GCM AndroidKeyStore e AAD por provider.
- Nenhuma chave embutida; callbacks de log terminal não escrevem entrada/saída.
- Clipboard OSC52 e leitura de clipboard solicitada pelo processo são negados.
- SAF importa snapshot limitado para pasta nova; falha limpa apenas staging recém-criado.
- APK debug pode ser depurado via ADB: não usar para credenciais de produção.
- Bibliotecas JNI ligadas com alinhamento ELF 16KiB e empacotamento legado comprimido
  para extração. Execução em telefone 16KiB ainda precisa teste específico.

Pendências antes de agente conectado: frontend de aprovação, cancellation/jobs,
rate limits/saída limitada durante execução, boundary de SO para processos,
redação de informações sensíveis em visualizações, bridge Pi sem bypass de ferramentas.
