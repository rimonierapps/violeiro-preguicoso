# 🎸 Violeiro Preguiçoso

App Android de cifras com identificação de música: descobre o que está tocando, abre a cifra e vai rolando a letra com os acordes para acompanhar no violão.

## Identificação de música

- **🎤 Microfone:** grava 10 s e envia para um serviço de reconhecimento de áudio. Com a posição e a duração da música, a cifra rola no tempo certo.
- **Compartilhar:** no app identificador, toque em *Compartilhar → Violeiro Preguiçoso*.
- **Automático:** com o leitor de notificações ligado, o app lê o resultado do app identificador e abre a cifra sozinho.
- **Player do celular:** se a música toca no próprio celular, o app segue a posição ao vivo (play, pausa, avanço).

## Tela da cifra

- Acordes em destaque e fonte ajustada à largura da tela (A− / A+).
- ▶/⏸ rolagem automática; 🐢/🐇 velocidade; arraste com o dedo para ajustar.
- ♭/♯ muda o tom mantendo o alinhamento com a letra.
- 🎤 ouve de novo para ressincronizar.
- Tela sempre acesa; cifras abertas ficam salvas e funcionam sem internet.

## Configuração

1. **Leitor de notificações:** toque em *Permitir leitura de notificações* na tela inicial.
2. **Token (⚙️):** token do serviço de reconhecimento de áudio usado pelo 🎤.
3. **📜 Registro:** mostra o que o app captou, para ajustes.

## Compilação

Cada push na `main` compila o APK pelo GitHub Actions, publica em *Releases* e envia pelo Telegram.
