# PHYSICAL DEVICE TESTS

Registrar modelo do telefone, Android, versão Gboard, SHA do APK e cada resultado.
Ainda não testado fisicamente. Sem chaves reais nestes testes.

1. Instalar APK Preview 0.3.0; abrir e permitir notificação. Ver prompt shell Android.
2. Digitar `pwd` e Enter. Deve mostrar diretório privado `workspaces/default`.
3. Gboard: executar `echo 'ação 😀'`. Verificar acentos/emoji sem duplicação.
4. Composição/autocomplete: digitar, corrigir palavra antes do Enter, apagar uma
   letra e uma palavra. Sugestões automáticas estão desativadas para preservar shell.
5. Ditado Gboard: ditar texto dentro de `echo '...'`, revisar antes do Enter;
   não ditar comandos perigosos. Registrar se botão de voz aparece e texto íntegro.
6. Colar texto pela barra: confirmar diálogo. Colar múltiplas linhas e verificar
   bracketed paste conforme shell. Selecionar saída por toque longo/copiar.
7. Tab, setas, Esc, Enter, Backspace, Ctrl por barra e teclado físico quando disponível.
8. `sleep 30`; C-c deve voltar ao prompt. `cat`; digitar linha, Enter, C-d.
   C-z deve suspender; `jobs`, `fg`, C-c. Não deixar processos de teste pendentes.
9. ANSI: `printf '\033[31mvermelho\033[0m\n'`; gerar saída e rolar scrollback.
10. Interativo: `vi teste.txt` **se disponível no Android**; sair com Esc `:q!`.
    Não tratar ausência de vi/nano como erro do PTY. TUI Linux: BusyBox vi após preparar Alpine.
11. `echo persistente > prova.txt`; Home, reabrir pela notificação, `cat prova.txt`.
12. Girar tela, trocar app/teclado, voltar. Verificar sessão/resize/scrollback.
13. Encerrar serviço pela notificação e reabrir; shell novo, arquivo deve permanecer.
14. Force-stop/reabrir: shell novo, `cat prova.txt` deve funcionar.
15. Atualizar APK **com mesma assinatura** e versionCode maior, sem desinstalar;
    verificar prova.txt e projetos importados. Se assinatura divergir, NÃO desinstalar
    para contornar: parar teste e registrar incompatibilidade.
16. Importar pasta pequena via SAF. Abrir pelo seletor Projetos e listar/ler.
    Reimportar: criar snapshot novo, sem substituir o anterior/original.
17. Verificar uso em segundo plano/bateria/OEM e saída da sessão sem tela aberta.

## Gates pendentes: Linux / Git / Pi

Registrar PENDENTE, não PASS, até executar no telefone:

18. **Mais → Preparar / abrir Linux**: acompanhar preparo; cancelar e repetir; verificar
    /etc/alpine-release, BusyBox, `tty` e `test -t 0` em ARM64.
19. Instalar Node/npm/Git/Pi conforme PROVISION.md; confirmar versões, DNS/TLS,
    `git init`, `git status`, `git diff` e Node com texto Unicode.
20. Configurar modelo/chave de teste, conectar Pi, enviar pedido, negar leitura e
    confirmar ausência de execução; permitir outra chamada; cancelar tarefa e revisar
    histórico. Nenhuma ferramenta deve executar antes da decisão explícita.
21. Fechar/reabrir Pi: conferir conversa salva; trocar projeto não mistura arquivos.
22. `exit` → **Nova sessão**; alterar fonte, girar tela; Ctrl/Alt visíveis e consumidos
    na próxima tecla. Verificar controles e dialogs em landscape/fonte grande.
23. Rascunho com ditado → revisar → inserir; cancelar cada confirmação deve preservar
    terminal. Testar seleção/cópia, colagem Unicode e multiline.
24. Exportar ZIP e conferir todos os arquivos; cancelar importação/exportação e testar
    pasta sem permissão, espaço insuficiente e symlink: falha clara sem corromper fonte.
25. Importar dois projetos; alternar conserva PTY/arquivos independentes; oito sessões
    ativas produzem mensagem clara no limite. Fechar uma e abrir outra.
26. Usar aparelho com páginas 16 KiB, confirmar tamanho com `getconf PAGE_SIZE` via adb
    e repetir PTY/PRoot/Node/Pi. Alinhamento ELF sozinho não conta como aprovação.

Preview tem outro ID em relação a 0.2.1: instala lado a lado, sem migração automática.
Atualização Preview→Preview requer mesma chave privada e versionCode maior.

# AUTOMATED TESTS

CI: lintDebug, testes unitários próprios + emulador upstream, assembleDebug,
assembleDebugAndroidTest, presença JNI em quatro ABIs. Emulador Android API34:
PTY real, comando/TTY/UTF-8 e arquivo entre processos; InputConnection real
setComposingText/commitText/finishComposingText → shell sem eco falso.
Testes de contrato não comprovam Gboard real/ditado, suspensão por OEM ou update.

## Regressão 0.2.1: abertura do teclado

- Atualizar sem desinstalar. O teclado deve abrir após o terminal receber foco.
- Fechar o teclado com Voltar. Tocar no botão **Teclado** no topo para reabrir.
- Tocar na área do terminal também deve reabrir. Digitar `echo teclado` e Enter.
- Confirmar o resultado no dispositivo com Gboard e depois testar ditado.

Teste automatizado adicional em Activity real verifica foco por toque, visibilidade
do IME na abertura, fechamento e reabertura pelo botão. Usa o teclado do emulador;
não substitui Gboard/voz no telefone.
