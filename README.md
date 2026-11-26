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

## ⭐ Minhas cifras (v2)

Suas cifras têm prioridade: quando a música é identificada, o app procura primeiro nelas.

- **Importar arquivos:** PDF, HTML ou TXT (vários de uma vez). Também dá para usar *Compartilhar* ou *Abrir com* a partir do gerenciador de arquivos.
- **Colar cifra:** cole o texto; o app reconhece as linhas de acordes para destacar e mudar o tom.
- **Do navegador:** selecione o texto da cifra e compartilhe com o app, ou compartilhe o link da página.
- **Lista de músicas:** um arquivo ou texto com uma música por linha (`Artista - Música`, `Música; Artista`…); o app procura e guarda cada uma.
- **☆ / ★** na tela da cifra guarda ou tira uma cifra das suas. Segure o dedo numa cifra da lista para renomear ou apagar.
- PDFs aparecem como páginas e rolam normalmente (sem mudança de tom).

## Configuração

1. **Leitor de notificações:** toque em *Permitir leitura de notificações* na tela inicial.
2. **Token (⚙️):** token do serviço de reconhecimento de áudio usado pelo 🎤.
3. **📜 Registro:** mostra o que o app captou, para ajustes.

## 💻 Versão web (Linux, Windows, qualquer navegador)

Endereço: <https://rimonierapps.github.io/violeiro-preguicoso/> — no Chrome/Edge dá para *Instalar app*; funciona sem internet depois de aberta uma vez.

- Mesma tela da cifra: rolagem, velocidade, tom, fonte, tela cheia.
- Teclado: **espaço** rola/pausa · **↑ ↓** velocidade · **+ −** tom · **F** tela cheia · **Esc** volta.
- Minhas cifras: importar PDF/HTML/TXT (ou arrastar para a página), colar texto, lista de músicas, **💾 Backup / ♻️ Restaurar** para levar as cifras para outro navegador.
- 🎤 pelo microfone do computador.
- As cifras ficam guardadas no próprio navegador.

**Buscar cifras na internet pelo navegador** precisa de um intermediário (o navegador bloqueia uma página de ler outro site). Para criar um grátis:

1. Crie uma conta em <https://dash.cloudflare.com> → *Workers & Pages* → *Create* → *Worker*.
2. Substitua o código pelo de [`web/intermediario-worker.js`](web/intermediario-worker.js) e publique.
3. Na página do app, em ⚙️, coloque `https://SEU-WORKER.workers.dev/?url=`.

Sem o intermediário, tudo funciona com as cifras importadas e coladas.

## Compilação

Cada push na `main` compila o APK pelo GitHub Actions, publica em *Releases* e envia pelo Telegram. Mudanças só em `web/` publicam a versão web (GitHub Pages) sem recompilar o APK.
