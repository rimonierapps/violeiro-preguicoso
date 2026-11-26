package br.rimonier.violeiro;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import java.io.File;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Mostra a cifra e rola sozinha, no tempo da música quando o app sabe onde ela está. */
public class CifraActivity extends Activity {
    private ScrollView sv;
    private TextView tvCifra, tvCabecalho, tvInfo, tvVel, tvTom;
    private LinearLayout caixa;      // conteúdo rolado: o texto da cifra ou as páginas do PDF
    private Button bEstrela;
    private Button bPlay;
    private final Handler h = new Handler(Looper.getMainLooper());

    private Cifra cifra;
    private String chaveAtual = "";
    private int semitons = 0;
    private float fonteSp = 0; // 0 = ajustar à largura da tela

    private boolean rolando = false;
    private float pos = 0;          // posição de rolagem (px)
    private float vel = 25;         // px por segundo
    private long ultimoQuadro = 0;
    private long pausaToqueAte = 0;
    private Sinc sinc;              // posição da música, se conhecida

    private final Runnable quadro = new Runnable() {
        @Override public void run() {
            passo();
            h.postDelayed(this, 16);
        }
    };

    private final BroadcastReceiver receptor = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Sinc s = Sinc.de(i);
            if (s == null) return;
            String artista = i.getStringExtra("artista"), musica = i.getStringExtra("musica");
            String k = Cifra.chave(artista, musica);
            if (k.equals(chaveAtual) || (cifra != null && k.equals(Cifra.chave(cifra.artista, cifra.titulo)))) {
                // mesma música: acompanha play/pausa/posição do player
                if (s.player) {
                    sinc = s;
                    if (s.tocando) sincronizar(); else pausar();
                }
            } else if (s.tocando) {
                carregar(artista, musica, null, s); // outra música começou
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(Ui.FUNDO);

        tvCabecalho = Ui.texto(this, "", 18, Ui.TEXTO);
        tvCabecalho.setTypeface(Typeface.DEFAULT_BOLD);
        tvCabecalho.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), 0);
        tvInfo = Ui.texto(this, "", 13, Ui.APAGADO);
        tvInfo.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 6));
        raiz.addView(tvCabecalho);
        raiz.addView(tvInfo);

        sv = new ScrollView(this);
        tvCifra = new TextView(this);
        tvCifra.setTypeface(Typeface.MONOSPACE);
        tvCifra.setTextColor(Ui.TEXTO);
        tvCifra.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 300));
        tvCifra.setText("Carregando…");
        caixa = new LinearLayout(this);
        caixa.setOrientation(LinearLayout.VERTICAL);
        caixa.addView(tvCifra);
        sv.addView(caixa);
        sv.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                pausaToqueAte = SystemClock.uptimeMillis() + 2500; // mexeu na mão: segue de onde parou
            }
            return false;
        });
        raiz.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // controles
        bPlay = Ui.botaoForte(this, "▶", v -> { if (rolando) pausar(); else tocar(); });
        tvVel = Ui.texto(this, "", 13, Ui.TEXTO);
        tvVel.setGravity(android.view.Gravity.CENTER);
        tvTom = Ui.texto(this, "", 13, Ui.TEXTO);
        tvTom.setGravity(android.view.Gravity.CENTER);
        LinearLayout controles = new LinearLayout(this);
        controles.setOrientation(LinearLayout.VERTICAL);
        controles.setBackgroundColor(Ui.CARTAO);
        controles.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        controles.addView(Ui.linha(this,
                bPlay,
                Ui.botao(this, "🐢", v -> mudarVel(0.85f)),
                tvVel,
                Ui.botao(this, "🐇", v -> mudarVel(1.18f)),
                Ui.botao(this, "A−", v -> mudarFonte(-1)),
                Ui.botao(this, "A+", v -> mudarFonte(1))));
        controles.addView(Ui.linha(this,
                Ui.botao(this, "♭", v -> mudarTom(-1)),
                tvTom,
                Ui.botao(this, "♯", v -> mudarTom(1)),
                Ui.botao(this, "🎤", v -> ouvirDeNovo()),
                bEstrela = Ui.botao(this, "☆", v -> alternarEstrela()),
                Ui.botao(this, "🌐", v -> abrirNoSite())));
        raiz.addView(controles);
        setContentView(raiz);

        atualizarRotulos();
        tratar(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        tratar(i);
    }

    private void tratar(Intent i) {
        Sinc s = Sinc.de(i);
        String url = i.getStringExtra("url");
        carregar(i.getStringExtra("artista"), i.getStringExtra("musica"), url, s);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OuvinteService.telasVisiveis++;
        IntentFilter f = new IntentFilter(OuvinteService.ACAO);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receptor, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receptor, f);
        ultimoQuadro = SystemClock.uptimeMillis();
        h.post(quadro);
    }

    @Override
    protected void onPause() {
        super.onPause();
        OuvinteService.telasVisiveis--;
        try { unregisterReceiver(receptor); } catch (Exception ignored) { }
        h.removeCallbacks(quadro);
    }

    // ---------- carregar ----------

    private void carregar(String artista, String musica, String url, Sinc s) {
        sinc = s;
        semitons = 0;
        chaveAtual = Cifra.chave(artista, musica);
        pausar();
        pos = 0;
        sv.scrollTo(0, 0);
        tvCabecalho.setText(url != null ? "Abrindo…" : (musica + (artista == null || artista.isEmpty() ? "" : " — " + artista)));
        tvInfo.setText("Procurando a cifra…");
        tvCifra.setText("");
        new Thread(() -> {
            Cifra c = null;
            String erro = null;
            try {
                if (url != null) {
                    c = Repositorio.ler(this, url);
                    if (c == null && !url.startsWith("local:")) c = Fonte.baixarCifra(url);
                } else {
                    c = Repositorio.acharNoHistorico(this, artista, musica);
                    if (c == null) c = Fonte.buscar(artista, musica);
                }
                if (c != null) Repositorio.salvar(this, c);
            } catch (Exception e) {
                erro = e.getMessage();
            }
            final Cifra fc = c;
            final String fe = erro;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (fc != null) {
                    mostrar(fc);
                } else if (fe != null) {
                    tvInfo.setText("Sem conexão? " + fe);
                    tvCifra.setText("Não consegui baixar a cifra.\n\nVerifique a internet e tente de novo.");
                } else {
                    // endereço direto não existe: abre a pesquisa do site para escolher
                    Repositorio.registrar(this, "Não achei pelo endereço: " + artista + " — " + musica);
                    Intent i = new Intent(this, BuscaWebActivity.class);
                    i.putExtra("q", ((artista == null ? "" : artista) + " " + (musica == null ? "" : musica)).trim());
                    if (sinc != null) sinc.colocar(i);
                    startActivity(i);
                    finish();
                }
            });
        }).start();
    }

    private void mostrar(Cifra c) {
        cifra = c;
        tvCabecalho.setText(c.titulo + (c.artista.isEmpty() ? "" : " — " + c.artista));
        render();
        atualizarEstrela();
        if (sinc != null && (sinc.temDuracao() || sinc.pos0 > 0)) {
            // espera o ajuste de fonte e o layout terminarem antes de medir a altura
            h.postDelayed(() -> { if (sinc != null && sinc.tocando) sincronizar(); }, 400);
        } else if (sinc != null && sinc.tocando) {
            h.postDelayed(this::tocar, 400); // reconhecida sem posição: rola na velocidade padrão
        }
        atualizarRotulos();
    }

    private void render() {
        if (cifra == null) return;
        // limpa páginas de PDF de uma cifra anterior
        while (caixa.getChildCount() > 1) caixa.removeViewAt(1);
        if (cifra.ehPdf()) {
            tvCifra.setVisibility(View.GONE);
            mostrarPdf();
            return;
        }
        tvCifra.setVisibility(View.VISIBLE);
        String t = Acordes.transporTexto(cifra.texto, semitons);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int maxLinha = 0, linha = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == Cifra.INI) {
                int e = t.indexOf(Cifra.FIM, i);
                if (e < 0) e = t.length();
                int ini = sb.length();
                sb.append(t, i + 1, e);
                sb.setSpan(new ForegroundColorSpan(Ui.ACORDE), ini, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), ini, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                linha += e - i - 1;
                i = e;
            } else if (ch == Cifra.FIM) {
                // sobra
            } else {
                sb.append(ch);
                if (ch == '\n') { maxLinha = Math.max(maxLinha, linha); linha = 0; } else linha++;
            }
        }
        maxLinha = Math.max(maxLinha, linha);
        final int larguraEmLetras = Math.max(20, maxLinha);
        tvCifra.setText(sb);
        if (fonteSp > 0) {
            tvCifra.setTextSize(fonteSp);
        } else {
            sv.post(() -> {
                // tamanho que faz a linha mais comprida caber na largura
                float disponivel = sv.getWidth() - tvCifra.getPaddingLeft() - tvCifra.getPaddingRight();
                if (disponivel <= 0) return;
                Paint p = new Paint();
                p.setTypeface(Typeface.MONOSPACE);
                p.setTextSize(100);
                float largura100 = p.measureText("M") * larguraEmLetras;
                float px = 100 * disponivel / largura100;
                float sp = px / getResources().getDisplayMetrics().scaledDensity;
                sp = Math.max(10, Math.min(22, sp));
                tvCifra.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
                fonteAuto = sp;
            });
        }
    }

    private float fonteAuto = 14;

    /** Desenha cada página do PDF como imagem, na largura da tela. */
    private void mostrarPdf() {
        sv.post(() -> {
            int largura = sv.getWidth();
            if (largura <= 0) largura = getResources().getDisplayMetrics().widthPixels;
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(new File(cifra.pdf), ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer pr = new PdfRenderer(fd)) {
                for (int i = 0; i < pr.getPageCount() && i < 40; i++) {
                    try (PdfRenderer.Page pg = pr.openPage(i)) {
                        int altura = (int) ((long) largura * pg.getHeight() / pg.getWidth());
                        Bitmap bmp = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        ImageView iv = new ImageView(this);
                        iv.setImageBitmap(bmp);
                        iv.setAdjustViewBounds(true);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.bottomMargin = Ui.dp(this, 6);
                        caixa.addView(iv, lp);
                    }
                }
                View fim = new View(this);
                caixa.addView(fim, new LinearLayout.LayoutParams(1, Ui.dp(this, 300)));
            } catch (Exception e) {
                tvCifra.setVisibility(View.VISIBLE);
                tvCifra.setText("Não consegui abrir o PDF: " + e.getMessage());
            }
        });
    }

    // ---------- rolagem ----------

    private int maxRolagem() {
        return Math.max(0, caixa.getHeight() - sv.getHeight());
    }

    private void passo() {
        long agora = SystemClock.uptimeMillis();
        float dt = (agora - ultimoQuadro) / 1000f;
        ultimoQuadro = agora;
        if (!rolando) return;
        if (agora < pausaToqueAte) { pos = sv.getScrollY(); return; }
        pos += vel * dt;
        int max = maxRolagem();
        if (pos >= max) { pos = max; }
        sv.scrollTo(0, (int) pos);
    }

    private void tocar() {
        rolando = true;
        pos = sv.getScrollY();
        ultimoQuadro = SystemClock.uptimeMillis();
        atualizarRotulos();
    }

    private void pausar() {
        rolando = false;
        atualizarRotulos();
    }

    /**
     * Usa a duração e a posição da música: a velocidade faz a cifra inteira passar
     * no tempo da música, e a posição atual fica a 1/3 da altura da tela.
     */
    private void sincronizar() {
        if (sinc == null || cifra == null) { tocar(); return; }
        int altura = caixa.getHeight() - (cifra.ehPdf() ? 0 : tvCifra.getPaddingBottom());
        if (altura <= 0) { tvCifra.post(this::sincronizar); return; }
        if (sinc.temDuracao()) {
            vel = altura * 1000f / sinc.dur;
            float y = (float) (altura * (double) sinc.posicaoAgora() / sinc.dur) - sv.getHeight() / 3f;
            pos = Math.max(0, Math.min(maxRolagem(), y));
            sv.scrollTo(0, (int) pos);
        }
        rolando = true;
        ultimoQuadro = SystemClock.uptimeMillis();
        atualizarRotulos();
    }

    private void mudarVel(float fator) {
        vel = Math.max(3, Math.min(400, vel * fator));
        atualizarRotulos();
    }

    private void mudarFonte(int d) {
        if (cifra != null && cifra.ehPdf()) { tvInfo.setText("PDF: aqui só dá para rolar (use dois dedos para zoom no leitor de PDF)"); return; }
        if (fonteSp <= 0) fonteSp = fonteAuto;
        fonteSp = Math.max(8, Math.min(32, fonteSp + d));
        tvCifra.setTextSize(TypedValue.COMPLEX_UNIT_SP, fonteSp);
    }

    private void mudarTom(int d) {
        if (cifra != null && cifra.ehPdf()) { tvInfo.setText("PDF: não dá para mudar o tom. Cole o texto da cifra para isso."); return; }
        semitons = Math.max(-11, Math.min(11, semitons + d));
        float rel = maxRolagem() > 0 ? sv.getScrollY() / (float) maxRolagem() : 0;
        render();
        sv.post(() -> { pos = rel * maxRolagem(); sv.scrollTo(0, (int) pos); });
        atualizarRotulos();
    }

    private void atualizarRotulos() {
        bPlay.setText(rolando ? "⏸" : "▶");
        tvVel.setText(Math.round(vel) + "\npx/s");
        tvTom.setText("Tom\n" + Acordes.nomeTom(semitons));
        if (cifra != null) {
            String origem = sinc == null ? "rolagem manual"
                    : sinc.player ? "seguindo o player" + (sinc.temDuracao() ? " (" + sinc.dur / 60000 + ":" + String.format("%02d", (sinc.dur / 1000) % 60) + ")" : "")
                    : sinc.temDuracao() ? "sincronizado pelo microfone" : "identificada";
            tvInfo.setText(origem + "  ·  toque na cifra e arraste para ajustar");
        }
    }

    // ---------- outros ----------

    private void ouvirDeNovo() {
        Identificador.ouvir(this, new Identificador.Retorno() {
            @Override public void status(String s) { tvInfo.setText(s); }
            @Override public void ok(String artista, String titulo, Sinc s) {
                String k = Cifra.chave(artista, titulo);
                boolean mesma = cifra != null && (k.equals(chaveAtual) || k.equals(Cifra.chave(cifra.artista, cifra.titulo)));
                if (mesma) {
                    sinc = s;
                    sincronizar();
                } else {
                    carregar(artista, titulo, null, s);
                }
            }
            @Override public void erro(String msg) { tvInfo.setText(msg); }
        });
    }

    private void alternarEstrela() {
        if (cifra == null) return;
        if (Repositorio.ehMinha(this, cifra.url)) {
            if (cifra.local()) { tvInfo.setText("Cifra sua: para apagar, segure o dedo nela na tela inicial."); return; }
            Repositorio.tirarDasMinhas(this, cifra.url, false);
        } else {
            Repositorio.salvarMinha(this, cifra);
        }
        atualizarEstrela();
    }

    private void atualizarEstrela() {
        bEstrela.setText(cifra != null && Repositorio.ehMinha(this, cifra.url) ? "★" : "☆");
    }

    private void abrirNoSite() {
        if (cifra == null) return;
        if (cifra.local()) { tvInfo.setText("Essa cifra é sua (importada), não tem página na internet."); return; }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(cifra.url)));
    }
}
