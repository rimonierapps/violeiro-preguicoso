package br.rimonier.violeiro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.net.Uri;
import android.widget.FrameLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private EditText edMusica, edArtista;
    private TextView status;
    private Button bPermissao;
    private LinearLayout listaRecentes, listaMinhas;
    private static final int REQ_ARQUIVOS = 21, REQ_LISTA = 22;
    private static boolean baixandoLista = false;
    private String ultimaAberta = "";

    private final BroadcastReceiver receptor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Sinc s = Sinc.de(i);
            String artista = i.getStringExtra("artista"), musica = i.getStringExtra("musica");
            if (s == null || !s.tocando) return;
            String k = Cifra.chave(artista, musica);
            if (k.equals(ultimaAberta)) return;
            abrirCifra(artista, musica, s);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = Repositorio.prefs(this);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 14);
        raiz.setPadding(pad, pad, pad, pad);

        TextView nome = Ui.texto(this, "🎸 Violeiro Preguiçoso v2", 24, Ui.TEXTO);
        nome.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        raiz.addView(nome);
        raiz.addView(Ui.texto(this, "Identifica a música e rola a cifra enquanto você toca.", 14, Ui.APAGADO));

        // --- busca manual ---
        raiz.addView(Ui.titulo(this, "Buscar cifra"));
        edMusica = campo("Música");
        edArtista = campo("Artista (opcional)");
        edArtista.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        edArtista.setOnEditorActionListener((v, a, e) -> { buscar(); return true; });
        raiz.addView(edMusica, Ui.larguraToda(this));
        raiz.addView(edArtista, Ui.larguraToda(this));
        raiz.addView(Ui.linha(this,
                Ui.botaoForte(this, "🔎 Buscar", v -> buscar()),
                Ui.botao(this, "🌐 Pesquisar no site", v -> pesquisarNoSite(
                        (edArtista.getText() + " " + edMusica.getText()).trim(), null))));

        // --- identificar ---
        raiz.addView(Ui.titulo(this, "O que está tocando?"));
        raiz.addView(Ui.botaoForte(this, "🎤 Ouvir e abrir a cifra", v -> ouvir()), Ui.larguraToda(this));
        raiz.addView(Ui.linha(this,
                Ui.botao(this, "Abrir app identificador", v -> abrirIdentificador())));
        raiz.addView(Ui.texto(this, "No app identificador, toque em Compartilhar → Violeiro Preguiçoso para abrir a cifra. "
                + "Com o leitor de notificações ligado, nem precisa compartilhar.", 13, Ui.APAGADO));

        // --- modo preguiçoso ---
        raiz.addView(Ui.titulo(this, "Modo preguiçoso"));
        raiz.addView(chave("Abrir a cifra quando o app identificador reconhecer", "seguir_identificador", true));
        raiz.addView(chave("Seguir o que toca no próprio celular e rolar no tempo da música", "seguir_player", true));
        raiz.addView(chave("Avisar com notificação quando o app estiver fechado", "notificar", true));
        bPermissao = Ui.botao(this, "", v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        raiz.addView(bPermissao, Ui.larguraToda(this));
        raiz.addView(Ui.linha(this,
                Ui.botao(this, "⚙️ Token", v -> configurarToken()),
                Ui.botao(this, "📜 Registro", v -> mostrarRegistro())));

        status = Ui.texto(this, "", 15, Ui.ACORDE);
        status.setPadding(0, Ui.dp(this, 10), 0, 0);
        raiz.addView(status);

        // --- minhas cifras ---
        raiz.addView(Ui.titulo(this, "⭐ Minhas cifras"));
        raiz.addView(Ui.texto(this, "Têm prioridade quando a música é identificada. Aceita PDF, HTML e TXT, "
                + "texto colado, ou compartilhar do navegador (texto selecionado ou o link da página).", 13, Ui.APAGADO));
        raiz.addView(Ui.linha(this,
                Ui.botao(this, "📥 Importar arquivos", v -> escolher(REQ_ARQUIVOS, true)),
                Ui.botao(this, "📋 Colar cifra", v -> dialogoColar(lerAreaTransferencia(), null))));
        raiz.addView(Ui.linha(this,
                Ui.botao(this, "📝 Lista (arquivo)", v -> escolher(REQ_LISTA, false)),
                Ui.botao(this, "📝 Colar lista", v -> dialogoLista(lerAreaTransferencia()))));
        listaMinhas = new LinearLayout(this);
        listaMinhas.setOrientation(LinearLayout.VERTICAL);
        raiz.addView(listaMinhas);

        // --- recentes ---
        raiz.addView(Ui.titulo(this, "Recentes (funcionam sem internet)"));
        listaRecentes = new LinearLayout(this);
        listaRecentes.setOrientation(LinearLayout.VERTICAL);
        raiz.addView(listaRecentes);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.FUNDO);
        sv.addView(raiz);
        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED && !p.getBoolean("pediu_notif", false)) {
            p.edit().putBoolean("pediu_notif", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
        }
        tratar(getIntent());
    }

    private EditText campo(String dica) {
        EditText e = new EditText(this);
        e.setHint(dica);
        e.setSingleLine(true);
        e.setTextColor(Ui.TEXTO);
        e.setHintTextColor(Ui.APAGADO);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        return e;
    }

    private CheckBox chave(String texto, String pref, boolean padrao) {
        CheckBox c = new CheckBox(this);
        c.setText(texto);
        c.setTextColor(Ui.TEXTO);
        c.setChecked(Repositorio.prefs(this).getBoolean(pref, padrao));
        c.setOnCheckedChangeListener((v, on) -> Repositorio.prefs(this).edit().putBoolean(pref, on).apply());
        return c;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        tratar(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OuvinteService.telasVisiveis++;
        IntentFilter f = new IntentFilter(OuvinteService.ACAO);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receptor, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receptor, f);
        boolean ok = OuvinteService.permitido(this);
        bPermissao.setText(ok ? "✅ Leitor de notificações ligado" : "🔔 Permitir leitura de notificações (necessário para o modo preguiçoso)");
        montarRecentes();
        montarMinhas();
    }

    @Override
    protected void onPause() {
        super.onPause();
        OuvinteService.telasVisiveis--;
        try { unregisterReceiver(receptor); } catch (Exception ignored) { }
    }

    private void montarMinhas() {
        listaMinhas.removeAllViews();
        for (Cifra c : Repositorio.minhas(this)) {
            String tipo = c.ehPdf() ? "📄 " : c.local() ? "📝 " : "⭐ ";
            TextView t = Ui.texto(this, tipo + c.titulo + (c.artista.isEmpty() ? "" : "  ·  " + c.artista), 16, Ui.TEXTO);
            t.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12));
            t.setOnClickListener(v -> abrirUrl(c.url));
            t.setOnLongClickListener(v -> { opcoesMinha(c); return true; });
            listaMinhas.addView(t);
            View sep = new View(this);
            sep.setBackgroundColor(0xFF2A2A2A);
            listaMinhas.addView(sep, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
        if (listaMinhas.getChildCount() == 0) {
            listaMinhas.addView(Ui.texto(this, "Nenhuma ainda. Importe, cole ou toque ☆ numa cifra.", 14, Ui.APAGADO));
        }
    }

    private void abrirUrl(String url) {
        Intent i = new Intent(this, CifraActivity.class);
        i.putExtra("url", url);
        startActivity(i);
    }

    private void opcoesMinha(Cifra c) {
        new AlertDialog.Builder(this)
                .setTitle(c.titulo)
                .setItems(new String[]{"Renomear", c.local() ? "Apagar" : "Tirar das minhas cifras"}, (d, w) -> {
                    if (w == 0) renomear(c);
                    else { Repositorio.tirarDasMinhas(this, c.url, c.local()); montarMinhas(); montarRecentes(); }
                })
                .show();
    }

    private void renomear(Cifra c) {
        EditText t = campo("Música"), a = campo("Artista");
        t.setText(c.titulo);
        a.setText(c.artista);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        l.addView(t);
        l.addView(a);
        new AlertDialog.Builder(this).setTitle("Renomear").setView(l)
                .setPositiveButton("Salvar", (d, w) -> {
                    Repositorio.renomear(this, c.url, t.getText().toString().trim(), a.getText().toString().trim());
                    montarMinhas();
                    montarRecentes();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    // ---------- importar ----------

    private void escolher(int req, boolean varios) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        if (req == REQ_ARQUIVOS) {
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "text/html", "text/plain", "text/*"});
        } else {
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "text/csv", "text/*"});
        }
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, varios);
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (req == REQ_ARQUIVOS) importarArquivos(uris, null);
        else if (req == REQ_LISTA && !uris.isEmpty()) {
            try {
                dialogoLista(new String(Importador.ler(this, uris.get(0)), StandardCharsets.UTF_8));
            } catch (Exception e) {
                status.setText("Não consegui ler a lista: " + e.getMessage());
            }
        }
    }

    private void importarArquivos(List<Uri> uris, String tipo) {
        status.setText("Importando " + uris.size() + " arquivo(s)…");
        new Thread(() -> {
            int ok = 0;
            Cifra ultima = null;
            StringBuilder falhas = new StringBuilder();
            for (Uri u : uris) {
                try {
                    String t = tipo != null ? tipo : getContentResolver().getType(u);
                    Cifra c = Importador.deArquivo(this, u, t);
                    if (c == null) { falhas.append("\n• ").append(Importador.nome(this, u)).append(" (tipo não aceito)"); continue; }
                    Repositorio.salvarMinha(this, c);
                    Repositorio.registrar(this, "Importada: " + c.titulo + (c.ehPdf() ? " (PDF)" : ""));
                    ultima = c;
                    ok++;
                } catch (Exception e) {
                    falhas.append("\n• ").append(Importador.nome(this, u)).append(": ").append(e.getMessage());
                }
            }
            final int fok = ok;
            final Cifra fu = ultima;
            runOnUiThread(() -> {
                status.setText(fok + " cifra(s) importada(s)." + (falhas.length() > 0 ? "\nNão importei:" + falhas : ""));
                montarMinhas();
                if (fok == 1 && uris.size() == 1 && fu != null) abrirUrl(fu.url);
            });
        }).start();
    }

    private String lerAreaTransferencia() {
        try {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                return t == null ? "" : t.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** Colar uma cifra: confere título/artista e o texto antes de salvar. */
    private void dialogoColar(String texto, String tituloSugerido) {
        EditText t = campo("Música"), a = campo("Artista");
        EditText corpo = new EditText(this);
        corpo.setTypeface(android.graphics.Typeface.MONOSPACE);
        corpo.setTextSize(12);
        corpo.setTextColor(Ui.TEXTO);
        corpo.setHint("Cole aqui a cifra (letra com acordes)");
        corpo.setHintTextColor(Ui.APAGADO);
        corpo.setMinLines(8);
        corpo.setMaxLines(14);
        corpo.setGravity(android.view.Gravity.TOP);
        corpo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        corpo.setHorizontallyScrolling(true);
        corpo.setText(texto);
        String[] nome = Importador.adivinharNome(texto);
        if (tituloSugerido != null && !tituloSugerido.isEmpty()) nome = Importador.separar(tituloSugerido);
        t.setText(nome[0]);
        a.setText(nome[1]);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        l.addView(t);
        l.addView(a);
        l.addView(corpo);
        new AlertDialog.Builder(this).setTitle("Colar cifra").setView(l)
                .setPositiveButton("Salvar e abrir", (d, w) -> {
                    String txt = corpo.getText().toString();
                    if (txt.trim().isEmpty()) { status.setText("Nada para salvar."); return; }
                    Cifra c = Importador.deTexto(t.getText().toString(), a.getText().toString(), txt);
                    Repositorio.salvarMinha(this, c);
                    montarMinhas();
                    abrirUrl(c.url);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    /** Lista de músicas: o app procura e guarda cada uma em "Minhas cifras". */
    private void dialogoLista(String texto) {
        EditText corpo = new EditText(this);
        corpo.setTextColor(Ui.TEXTO);
        corpo.setHint("Uma música por linha, por exemplo:\nLegião Urbana - Tempo Perdido\nAsa Branca; Luiz Gonzaga");
        corpo.setHintTextColor(Ui.APAGADO);
        corpo.setMinLines(6);
        corpo.setMaxLines(14);
        corpo.setGravity(android.view.Gravity.TOP);
        corpo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        corpo.setText(texto);
        FrameLayout f = new FrameLayout(this);
        f.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        f.addView(corpo);
        new AlertDialog.Builder(this).setTitle("Lista de músicas").setView(f)
                .setPositiveButton("Buscar todas", (d, w) -> baixarLista(Importador.lista(corpo.getText().toString())))
                .setNegativeButton("Cancelar", null).show();
    }

    private void baixarLista(List<String[]> musicas) {
        if (musicas.isEmpty()) { status.setText("A lista está vazia."); return; }
        if (baixandoLista) { status.setText("Já estou baixando uma lista…"); return; }
        baixandoLista = true;
        new Thread(() -> {
            int ok = 0, i = 0;
            StringBuilder faltou = new StringBuilder();
            for (String[] m : musicas) {
                i++;
                final String aviso = "Buscando " + i + " de " + musicas.size() + ": " + m[1];
                runOnUiThread(() -> status.setText(aviso));
                try {
                    Cifra c = Repositorio.acharNoHistorico(this, m[0], m[1]);
                    if (c == null) c = Fonte.buscar(m[0], m[1]);
                    if (c != null) { Repositorio.salvarMinha(this, c); ok++; }
                    else faltou.append("\n• ").append(m[1]);
                    Thread.sleep(700); // sem pressa, para não sobrecarregar o site
                } catch (Exception e) {
                    faltou.append("\n• ").append(m[1]).append(" (").append(e.getMessage()).append(")");
                }
            }
            Repositorio.registrar(this, "Lista: " + ok + " de " + musicas.size() + " encontradas." + faltou);
            final int fok = ok;
            runOnUiThread(() -> {
                baixandoLista = false;
                status.setText("Lista: " + fok + " de " + musicas.size() + " cifras guardadas."
                        + (faltou.length() > 0 ? "\nNão achei (use 🌐 Pesquisar no site ou cole):" + faltou : ""));
                montarMinhas();
            });
        }).start();
    }

    /** Link compartilhado do navegador: página do site de cifras abre direto; outra página é importada. */
    private void importarLink(String url) {
        Uri u = Uri.parse(url);
        String daFonte = Fonte.urlDeMusica(u);
        if (daFonte != null) {
            status.setText("Abrindo a cifra do link…");
            abrirUrl(daFonte);
            return;
        }
        status.setText("Lendo a página…");
        new Thread(() -> {
            try {
                String html = Fonte.baixar(url);
                if (html == null) throw new Exception("a página não respondeu");
                Cifra c = Importador.deHtml(html, u.getLastPathSegment());
                runOnUiThread(() -> {
                    status.setText("Confira o texto e salve.");
                    dialogoColar(c.texto.replace(String.valueOf(Cifra.INI), "").replace(String.valueOf(Cifra.FIM), ""),
                            (c.artista.isEmpty() ? "" : c.artista + " - ") + c.titulo);
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Não consegui ler a página: " + e.getMessage()
                        + "\nSelecione o texto da cifra no navegador e compartilhe/cole."));
            }
        }).start();
    }

    private void montarRecentes() {
        listaRecentes.removeAllViews();
        for (Cifra c : Repositorio.historico(this)) {
            TextView t = Ui.texto(this, c.titulo + "  ·  " + c.artista, 16, Ui.TEXTO);
            t.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12));
            t.setOnClickListener(v -> {
                Intent i = new Intent(this, CifraActivity.class);
                i.putExtra("url", c.url);
                startActivity(i);
            });
            listaRecentes.addView(t);
            View sep = new View(this);
            sep.setBackgroundColor(0xFF2A2A2A);
            listaRecentes.addView(sep, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
        if (listaRecentes.getChildCount() == 0) {
            listaRecentes.addView(Ui.texto(this, "Nenhuma cifra aberta ainda.", 14, Ui.APAGADO));
        }
    }

    // ---------- ações ----------

    private void buscar() {
        String m = edMusica.getText().toString().trim(), a = edArtista.getText().toString().trim();
        if (m.isEmpty()) { Toast.makeText(this, "Digite o nome da música", Toast.LENGTH_SHORT).show(); return; }
        abrirCifra(a, m, null);
    }

    private void abrirCifra(String artista, String musica, Sinc s) {
        ultimaAberta = Cifra.chave(artista, musica);
        Intent i = new Intent(this, CifraActivity.class);
        i.putExtra("artista", artista);
        i.putExtra("musica", musica);
        if (s != null) s.colocar(i);
        startActivity(i);
    }

    private void pesquisarNoSite(String q, Sinc s) {
        Intent i = new Intent(this, BuscaWebActivity.class);
        i.putExtra("q", q);
        if (s != null) s.colocar(i);
        startActivity(i);
    }

    private void ouvir() {
        Identificador.ouvir(this, new Identificador.Retorno() {
            @Override public void status(String s) { status.setText(s); }
            @Override public void ok(String artista, String titulo, Sinc sinc) {
                status.setText("Achei: " + titulo + " — " + artista);
                edMusica.setText(titulo);
                edArtista.setText(artista);
                abrirCifra(artista, titulo, sinc);
            }
            @Override public void erro(String msg) { status.setText(msg); }
        });
    }

    /** Abre o primeiro app de identificação de música instalado. */
    private void abrirIdentificador() {
        PackageManager pm = getPackageManager();
        for (String pacote : OuvinteService.IDENTIFICADORES_ORDEM) {
            Intent i = pm.getLaunchIntentForPackage(pacote);
            if (i != null) { startActivity(i); return; }
        }
        status.setText("Nenhum app identificador instalado.");
    }

    private void configurarToken() {
        EditText e = campo("token");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        e.setText(Repositorio.prefs(this).getString("token_reconhecimento", ""));
        new AlertDialog.Builder(this)
                .setTitle("Token do reconhecimento")
                .setMessage("O botão 🎤 usa um serviço de reconhecimento de áudio. "
                        + "Sem token funciona poucas vezes por dia; com token dá bem mais.")
                .setView(e)
                .setPositiveButton("Salvar", (d, w) -> Repositorio.prefs(this).edit()
                        .putString("token_reconhecimento", e.getText().toString().trim()).apply())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarRegistro() {
        TextView t = Ui.texto(this, Repositorio.registro(this), 12, Ui.TEXTO);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        new AlertDialog.Builder(this)
                .setTitle("Registro")
                .setView(sv)
                .setPositiveButton("Fechar", null)
                .setNeutralButton("Limpar", (d, w) -> Repositorio.prefs(this).edit().remove("registro").apply())
                .show();
    }

    // ---------- "Compartilhar" vindo de outros apps ----------

    private void tratar(Intent i) {
        if (i == null || i.getAction() == null) return;
        String acao = i.getAction();
        if (!Intent.ACTION_SEND.equals(acao) && !Intent.ACTION_SEND_MULTIPLE.equals(acao) && !Intent.ACTION_VIEW.equals(acao)) return;
        setIntent(new Intent(this, MainActivity.class)); // não reprocessar ao girar a tela

        // arquivos (PDF, HTML, TXT) compartilhados ou abertos com o app
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_VIEW.equals(acao) && i.getData() != null) uris.add(i.getData());
        if (Intent.ACTION_SEND.equals(acao)) {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        }
        if (Intent.ACTION_SEND_MULTIPLE.equals(acao)) {
            ArrayList<Uri> l = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) uris.addAll(l);
        }
        if (!uris.isEmpty()) {
            String tipo = i.getType();
            importarArquivos(uris, tipo != null && !tipo.contains("*") ? tipo : null);
            return;
        }

        String txt = i.getStringExtra(Intent.EXTRA_TEXT);
        String assunto = i.getStringExtra(Intent.EXTRA_SUBJECT);
        Repositorio.registrar(this, "Compartilhado: «" + assunto + "» / «"
                + (txt != null && txt.length() > 200 ? txt.substring(0, 200) + "…" : txt) + "»");
        if (txt == null) txt = "";
        String semEspaco = txt.trim();
        // só um link (navegador): importar a página
        if (semEspaco.matches("https?://\\S+") && !semEspaco.contains("/track/")) {
            importarLink(semEspaco);
            return;
        }
        // texto grande (cifra selecionada no navegador): colar
        if (semEspaco.split("\n").length >= 4 || semEspaco.length() > 250) {
            dialogoColar(txt, assunto);
            return;
        }
        String[] am = interpretar(txt, assunto);
        if (am != null) {
            edMusica.setText(am[1]);
            edArtista.setText(am[0]);
            abrirCifra(am[0], am[1], null);
        } else {
            String limpo = limpar(txt == null ? (assunto == null ? "" : assunto) : txt);
            edMusica.setText(limpo);
            status.setText("Não entendi o texto compartilhado. Ajuste o nome e toque em Buscar.");
        }
    }

    private static final Pattern P_FRASE = Pattern.compile(
            "(?i)(?:descobrir|descobri|discover|discovered|find|found|encontrar|encontrei|achei|identifiquei|ouvindo|listening to)"
                    + "\\s+[\"“]?(.+)[\"”]?\\s+(?:by|de|por|da|do)\\s+(.+?)\\s*(?:[.!]?\\s*(?:no|on|with|com|in)\\s+(?:o\\s+)?(?:shazam|soundhound)[^\\n]*)?[.!]?\\s*$");

    static String limpar(String s) {
        return s.replaceAll("https?://\\S+", "").replaceAll("\\s+", " ").trim();
    }

    /** Devolve {artista, música} ou null. */
    static String[] interpretar(String txt, String assunto) {
        if (txt != null) {
            String s = limpar(txt);
            Matcher m = P_FRASE.matcher(s);
            if (m.find()) {
                String titulo = m.group(1).replaceAll("[\"“”]", "").trim();
                String artista = m.group(2).replaceAll("[\"“”]", "").trim();
                if (!titulo.isEmpty() && !artista.isEmpty()) return new String[]{artista, titulo};
            }
            // "Música - Artista" ou "Música de Artista"
            // (a busca testa as duas ordens)
            if (s.contains(" - ")) return new String[]{"", s};
            Matcher by = Pattern.compile("(?i)^(.+)\\s+(?:by|de|por)\\s+(.+)$").matcher(s);
            if (by.find() && s.length() < 120) return new String[]{by.group(2).trim(), by.group(1).trim()};
        }
        if (assunto != null && assunto.contains(" - ")) return new String[]{"", assunto.trim()};
        return null;
    }
}
