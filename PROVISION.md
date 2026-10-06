# Linux persistente e Pi — 0.3.0

O APK inicia no shell Android; o usuário prepara Linux por **Mais → Preparar / abrir Linux**.
Não reduzimos target 34. PRoot e seu loader são executáveis nativos empacotados no
APK; rootfs privado é interpretado pelo loader. O preparo só publica staging após
uma execução real de `/bin/sh` e BusyBox retornar sucesso.

## Fontes fixas

- termux/proot: `a179d3e8a4e045aaa1fb8cc3284f23509d96d353`;
- talloc 2.4.2: hashes e build em `scripts/build-proot.sh`;
- patch local x86_64: `fork` traduzido para `clone(SIGCHLD)` equivalente, pois
  o filtro Android permite clone e não o syscall fork x86_64. Sem novas permissões
  ou namespaces. Patch e fonte exata ficam disponíveis no repositório;
  referência: https://android.googlesource.com/platform/bionic/+/android-14.0.0_r1/libc/SECCOMP_ALLOWLIST_APP.TXT;
- Alpine minirootfs **3.23.0** x86_64/aarch64: URL oficial e SHA-256 em `LinuxRuntime.kt`;
- Pi **1.0.4**, Node exigido pelo upstream >=22.19.0.

Download limitado a 8 MiB; armazenamento mínimo 128 MiB; extração limitada a
512 MiB/20.000 entradas, checksum tar e contenção de links. Falha/cancelamento
remove somente staging novo. Runtime existente não é substituído ou apagado.
DNS inicial 1.1.1.1/8.8.8.8, informado antes do preparo; edite `/etc/resolv.conf`
se necessário. PRoot não é fronteira de segurança do sistema operacional.

## Instalação explícita de ferramentas

Abra o shell Linux após preparar. Execute e confirme as versões:

```sh
apk add --no-cache nodejs npm git ca-certificates
node --version
npm --version
git --version
npm install -g --ignore-scripts @earendil-works/pi-coding-agent@1.0.4
pi --version
```

A versão Pi precisa ser 1.0.4: outra versão é recusada pelo handshake. Pacotes apk
são verificados pelo Alpine; as versões Node/Git seguem o repositório v3.23 e podem
mudar. Não oferecemos instalação de Debian/apt, nem prometemos versões arbitrárias.
Sem scripts npm de instalação automática. Configure provider/model/chave pelo painel
**Pi Agent → Modelo e chave**, depois **Conectar** e **Enviar**. As chaves ficam em
Android Keystore e entram só no ambiente do subprocesso, nunca no prompt ou workspace.

## Bridge e aprovação

RPC usa pipes stdout/stderr separados, JSON por linha, ID de requisição e eventos
`agent_settled`. Ack de prompt não é conclusão. Extensões de projeto, MCP, skills,
context files e ferramentas nativas são desativados. Uma extensão explícita registra
read/write/edit/bash/workspace/clipboard/share e encaminha cada operação ao gate Android.
A UI mostra parâmetros exatos; aprovação expira após 120 segundos e pode ser negada.
Shell tem timeout de 30 segundos. Cancelar envia abort/clear_queue, interrompe jobs e
nega aprovações pendentes. Histórico guarda decisão/resultado sem argumentos ou chaves;
Pi guarda conversa JSONL privada por projeto.

## Verificação e gates

`RuntimeDeviceTest` testa o rootfs hash-pinned em Android API34.
`NetworkRuntimeProof` testa apk, Node/Git/Pi reais e handshake RPC no Android, sem
modelo pago. Para executar manualmente:

```sh
gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.runtimeNetworkProof=true
```

ARM64 em aparelho, páginas 16 KiB, restrições OEM e uso físico Gboard/voz precisam
registro no plano de testes. Alinhamento ELF não comprova execução nessas condições.
A prova RPC em Linux host só valida protocolo/extensão; não substitui prova Android.
