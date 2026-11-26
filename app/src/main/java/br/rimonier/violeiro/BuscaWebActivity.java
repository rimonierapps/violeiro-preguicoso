package br.rimonier.violeiro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.URLEncoder;

/**
 * Quando o endereço direto não existe, mostra uma pesquisa na web.
 * Ao tocar num resultado do site de cifras, a cifra abre no modo de acompanhar.
 */
public class BuscaWebActivity extends Activity {
    private WebView web;
    private String q;
    private boolean abriu = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        q = getIntent().getStringExtra("q");
        if (q == null) q = "";

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(Ui.FUNDO);
        TextView aviso = Ui.texto(this, "Toque na música certa: a cifra abre no modo de acompanhar.", 14, Ui.ACORDE);
        aviso.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 4));
        raiz.addView(aviso);
        raiz.addView(Ui.linha(this,
                Ui.botao(this, "Site de cifras", v -> web.loadUrl(Fonte.BASE + "?q=" + enc(q))),
                Ui.botao(this, "Buscador 1", v -> web.loadUrl("https://www.google.com/search?q=" + enc("site:" + Fonte.DOMINIO + " " + q))),
                Ui.botao(this, "Buscador 2", v -> web.loadUrl("https://html.duckduckgo.com/html/?q=" + enc("site:" + Fonte.DOMINIO + " " + q)))));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return interceptar(r.getUrl());
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                if (interceptar(Uri.parse(url))) v.stopLoading();
            }
        });
        raiz.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(raiz);

        web.loadUrl("https://www.google.com/search?q=" + enc("site:" + Fonte.DOMINIO + " " + q));
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private boolean interceptar(Uri u) {
        if (u == null || abriu) return false;
        Uri alvo = u;
        String host = u.getHost() == null ? "" : u.getHost();
        // links de redirecionamento dos buscadores
        if (host.contains("google.") && "/url".equals(u.getPath())) {
            String d = u.getQueryParameter("q");
            if (d == null) d = u.getQueryParameter("url");
            if (d != null) alvo = Uri.parse(d);
        } else if (host.contains("duckduckgo.com") && u.getQueryParameter("uddg") != null) {
            alvo = Uri.parse(u.getQueryParameter("uddg"));
        }
        String url = Fonte.urlDeMusica(alvo);
        if (url == null) return false;
        abriu = true;
        Intent i = new Intent(this, CifraActivity.class);
        i.putExtra("url", url);
        Sinc s = Sinc.de(getIntent());
        if (s != null) s.colocar(i);
        startActivity(i);
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
