package br.rimonier.violeiro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
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
    private LinearLayout listaRecentes;
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

        TextView nome = Ui.texto(this, "🎸 Violeiro Preguiçoso v1", 24, Ui.TEXTO);
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        OuvinteService.telasVisiveis--;
        try { unregisterReceiver(receptor); } catch (Exception ignored) { }
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
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        String txt = i.getStringExtra(Intent.EXTRA_TEXT);
        String assunto = i.getStringExtra(Intent.EXTRA_SUBJECT);
        Repositorio.registrar(this, "Compartilhado: «" + assunto + "» / «" + txt + "»");
        setIntent(new Intent(this, MainActivity.class)); // não reprocessar ao girar a tela
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
