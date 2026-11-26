# 🎸 Violeiro Preguiçoso

App Android: descobre a música que está tocando, abre a cifra do **Cifra Club** e vai rolando a letra com os acordes enquanto você acompanha no violão.

## Como o app descobre a música (as duas abordagens)

| Caminho | Como funciona | Rolagem |
|---|---|---|
| **🎤 Do zero (microfone)** | Grava 10 s e manda para o reconhecimento da [AudD](https://audd.io) (o mesmo tipo de "impressão digital" do Shazam). | **Sincronizada**: a AudD diz em que ponto da música a gravação caiu e a duração total. |
| **Shazam / SoundHound – compartilhar** | No resultado do Shazam/SoundHound toque em *Compartilhar → Violeiro Preguiçoso*. | Velocidade padrão, ajustável. |
| **Shazam / SoundHound – automático** | Com o leitor de notificações ligado, o app lê a notificação de resultado (Auto Shazam / Pop-up) e abre a cifra sozinho. | Velocidade padrão, ajustável. |
| **Player do celular** | Se a música toca no próprio celular (Spotify, YouTube Music, Deezer…), o app lê título, artista e a posição exata. | **Segue o player ao vivo**, inclusive pausa e avanço. |

> O Shazam e o SoundHound não têm API pública para outros apps. Por isso a integração é pelo *Compartilhar* e pelas notificações. O reconhecimento "do zero" depende de um serviço de impressão digital de áudio (a AudD), porque reconhecer música exige um banco com milhões de faixas.

## A tela da cifra

- Acordes em destaque, fonte que se ajusta à largura da tela (A− / A+ para mudar).
- ▶/⏸ rolagem automática; 🐢/🐇 velocidade; arrastar com o dedo ajusta e ela segue dali.
- ♭/♯ muda o tom (transposição mantendo o alinhamento com a letra).
- 🎤 ouve de novo para ressincronizar (ou trocar de música).
- Tela fica acesa. Cifras abertas ficam salvas e funcionam sem internet.

## Busca no Cifra Club

O Cifra Club não tem API pública. O app monta o endereço (`cifraclub.com.br/artista/musica/`), testando variações (sem "ao vivo", sem "feat.", ordem invertida). Se não achar, abre uma pesquisa (Google / Cifra Club / DuckDuckGo): tocando num resultado do Cifra Club, a cifra abre no modo de acompanhar. Uso pessoal.

## Configuração

1. **Leitor de notificações**: na tela inicial, toque em *Permitir leitura de notificações* (necessário para o modo automático e para seguir o player).
2. **Token da AudD** (⚙️): sem token funciona poucas vezes por dia; crie um token em <https://dashboard.audd.io>.
3. **📜 Registro**: mostra o que o app captou (notificações do Shazam, compartilhamentos, resultados da AudD). Útil para ajustar o reconhecimento.

## Compilação

Cada push na `main` compila o APK pelo GitHub Actions, publica em *Releases* e envia pelo Telegram (segredos `TELEGRAM_TOKEN` e `TELEGRAM_CHAT_ID`).
