# PHYSICAL DEVICE TESTS

Registrar modelo do telefone, Android, versão Gboard, SHA do APK e cada resultado.
Ainda não testado fisicamente. Sem chaves reais nestes testes.

1. Instalar APK debug de CI; abrir e permitir notificação. Ver prompt shell Android.
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
    Não tratar ausência de vi/nano como erro do PTY. TUI Linux aguarda Debian.
11. `echo persistente > prova.txt`; Home, reabrir pela notificação, `cat prova.txt`.
12. Girar tela, trocar app/teclado, voltar. Verificar sessão/resize/scrollback.
13. Encerrar serviço pela notificação e reabrir; shell novo, arquivo deve permanecer.
14. Force-stop/reabrir: shell novo, `cat prova.txt` deve funcionar.
15. Atualizar APK **com mesma assinatura** e versionCode maior, sem desinstalar;
    verificar prova.txt e projetos importados. Se assinatura divergir, NÃO desinstalar
    para contornar: parar teste e registrar incompatibilidade.
16. Importar pasta pequena via SAF. Usar caminho informado no diálogo e listar/ler.
    Reimportar: criar snapshot novo, sem substituir o anterior/original.
17. Verificar uso em segundo plano/bateria/OEM e saída da sessão sem tela aberta.

## Gates pendentes: Linux / Git / Pi

Não disponíveis neste build; registrar N/A, não PASS:
Debian/bash, apt, git init/status/diff, curl/wget/ssh, python3/node/npm,
instalação manual de Pi/Codex/Claude e Pi com ferramentas Android.

# AUTOMATED TESTS

CI: lintDebug, testes unitários próprios + emulador upstream, assembleDebug,
assembleDebugAndroidTest, presença JNI em quatro ABIs. Emulador Android API34:
PTY real, comando/TTY/UTF-8 e arquivo entre processos; InputConnection real
setComposingText/commitText/finishComposingText → shell sem eco falso.
Testes de contrato não comprovam Gboard real/ditado, suspensão por OEM ou update.
