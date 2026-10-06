# Segurança e limites

- `ReadOnlyGate` permite READ não sensível; WRITE/EXECUTE/DESTRUCTIVE negados.
- Clipboard READ também exige aprovação por ser conteúdo externo/sensível.
- PermissionRequest inclui sessão, operação, recurso e cópia imutável dos parâmetros:
  o frontend mostra a operação exata, sem aprovar alterações posteriores.
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

## Pi conectado em 0.3.0

Toda ferramenta bridge exige aprovação, expira em 120 segundos e mostra parâmetros.
Ferramentas nativas, MCP e descoberta automática de recursos são desativadas.
RPC limita registros a 1 MiB, jobs pendentes a 16 e UI a 64 KiB de texto. Shell tem
30 segundos. Logs de stderr são drenados sem persistência de dados sensíveis.
Conversas Pi são privadas, mas podem conter conteúdo fornecido pelo usuário/modelo;
não equivalem ao audit metadata sem argumentos. Não inserir segredos em prompts.
A versão Pi é fixada em 1.0.4; atualização exige revisar contrato e retestar bridge.

Gates restantes: execução física ARM64/16 KiB, Gboard/voz, modelo com credencial do
usuário e políticas OEM. Assinatura Preview privada deve ser secret externo, nunca Git.
