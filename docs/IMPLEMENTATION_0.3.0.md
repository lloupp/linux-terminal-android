# Entrega das oportunidades aprovadas — Preview 0.3.0

Autorização: implementar as 12 oportunidades da auditoria. Branch separada,
PR #3 sobre PR #2; sem merge. Não inclui automação irrestrita do agente, sincronização
SAF bidirecional ou distribuição Play Store. Auditoria original preservada.

| # | Oportunidade | Entrega / evidência | Limite de validação |
|---|---|---|---|
| 1 | Cópia manual | Callback explícito da view e seleção, paste confirmado; OSC52 negado | Teste Android manual-copy e plano físico |
| 2 | Reiniciar shell fechado | Nova sessão após exit, mantém workspace | Teste Android projeto/restart |
| 3 | Gboard/voz | IME/composição, editor de rascunho e plano físico | Sem aparelho físico; não declarar PASS |
| 4 | Node/Git/runtime | PRoot+loader quatro ABIs; Alpine privado hash-pinned ARM64/x86_64 | Prova Android com rede na CI; ARM64 físico pendente |
| 5 | Assinatura/update | Preview separado, versionCode 4, keystore externo e secrets opcionais CI | Não migra ID antigo; CI estável requer secrets privados |
| 6 | Projetos recentes | IDs privados estáveis, nomes de pastas importadas, seleção e até 8 shells por projeto/backend | Sem sincronização automática com fonte SAF |
| 7 | ZIP export | Arquivos/dirs vazios/Unicode, staging, cancelamento e limpeza de destino parcial | 2.000 documentos/64 MiB; rejeita links |
| 8 | Modificadores/fonte | Ctrl/Alt visíveis, one-shot, fonte persistida e pinch | Plano físico inclui teclado/landscape |
| 9 | Importação progress/cancel | Worker independente da Activity, staging atômico, feedback e limites | Providers SAF/OEM reais precisam teste físico |
| 10 | Ambiente/capacidades | ABI/Android/runtime e sondagem Node/npm/Git/Pi real em worker | Não instala pacotes silenciosamente |
| 11 | Rascunho/ditado | EditText multiline revisável, inserção explícita e aviso newline | Microfone depende do teclado; sem ASR próprio |
| 12 | Pi RPC/approval/jobs/history | Pi 1.0.4, pipes JSONL, gate por chamada, cancelamento, Keystore, sessões e audit limitado | Sem chamada paga; revisão de protocolo ao atualizar Pi |

## Revisão da alteração

Revisão separada da implementação inicial verificou contenção, limites e lifecycle.
Corrigidos: incompatibilidade API24 de espera de Process/flags IME, publicação prematura
de sessão ativa, corrida de jobs, erro de modelo sobrescrito por conclusão, timeout
RPC ausente, colagem truncada/sem vínculo de sessão, fila de requisições sem limite, header tar raiz com payload e configuração que colocaria chave privada
no Git. Chave retirada de todos os commits publicados e usada somente externamente.
Nenhuma chave/provider real ou conteúdo de terminal é escrito em logs de diagnóstico.

## Verificação local

- JDK17/SDK34/NDK26.1.10909125: quatro ABIs nativas construídas;
- lintDebug, assembleDebug e assembleDebugAndroidTest: sucesso;
- **158 testes JVM, zero falhas/erros/skip**, incluindo segurança tar, ZIP,
  Unicode/JSONL, contenção, edição e testes upstream do emulador;
- Pi 1.0.4 real no host Linux: get_state por RPC e extensão carregada sem erros;
  esta prova valida contrato, não execução Android;
- Emulador local sem KVM não completou boot confiável; CI usa KVM e testes Android reais.

Resultados Android finais devem ser consultados no PR/CI antes de considerar este
Preview apto para dados importantes. Teste pago/provider, Gboard/voz, ARM64/16 KiB
físicos e políticas OEM não são comprovados por build ou testes JVM.
