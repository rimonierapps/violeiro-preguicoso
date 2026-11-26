package br.rimonier.violeiro;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Guarda as cifras já abertas (funcionam sem internet), o histórico e o registro. */
public class Repositorio {

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("violeiro", Context.MODE_PRIVATE);
    }

    private static File arquivo(Context c, String url) {
        File d = new File(c.getFilesDir(), "cifras");
        d.mkdirs();
        String nome = Fonte.slug(url.replace(Fonte.BASE, ""));
        return new File(d, nome + ".json");
    }

    public static void salvar(Context c, Cifra cifra) {
        try {
            byte[] b = cifra.paraJson(true).toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream o = new FileOutputStream(arquivo(c, cifra.url))) {
                o.write(b);
            }
            JSONArray velho = new JSONArray(prefs(c).getString("historico", "[]"));
            JSONArray novo = new JSONArray();
            novo.put(cifra.paraJson(false));
            for (int i = 0; i < velho.length() && novo.length() < 60; i++) {
                JSONObject j = velho.getJSONObject(i);
                if (!cifra.url.equals(j.optString("url"))) novo.put(j);
            }
            prefs(c).edit().putString("historico", novo.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static Cifra ler(Context c, String url) {
        try {
            File f = arquivo(c, url);
            if (!f.exists()) return null;
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int lido = 0;
                while (lido < b.length) {
                    int n = in.read(b, lido, b.length - lido);
                    if (n < 0) break;
                    lido += n;
                }
            }
            return Cifra.deJson(new JSONObject(new String(b, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Cifra> historico(Context c) {
        List<Cifra> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("historico", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(Cifra.deJson(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return l;
    }

    // ---------- minhas cifras (favoritas e importadas) ----------

    public static List<Cifra> minhas(Context c) {
        List<Cifra> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("minhas", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(Cifra.deJson(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return l;
    }

    private static void gravarMinhas(Context c, List<Cifra> l) {
        try {
            JSONArray a = new JSONArray();
            for (Cifra x : l) a.put(x.paraJson(false).put("pdf", x.pdf));
            prefs(c).edit().putString("minhas", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static boolean ehMinha(Context c, String url) {
        for (Cifra x : minhas(c)) if (x.url.equals(url)) return true;
        return false;
    }

    /** Salva a cifra e coloca (ou atualiza) em "Minhas cifras". */
    public static void salvarMinha(Context c, Cifra cifra) {
        salvar(c, cifra);
        List<Cifra> l = minhas(c);
        List<Cifra> novo = new ArrayList<>();
        novo.add(cifra);
        for (Cifra x : l) if (!x.url.equals(cifra.url)) novo.add(x);
        gravarMinhas(c, novo);
    }

    public static void tirarDasMinhas(Context c, String url, boolean apagar) {
        List<Cifra> novo = new ArrayList<>();
        for (Cifra x : minhas(c)) {
            if (!x.url.equals(url)) { novo.add(x); continue; }
            if (apagar && x.local()) {
                arquivo(c, url).delete();
                if (x.ehPdf()) new File(x.pdf).delete();
            }
        }
        gravarMinhas(c, novo);
        if (apagar) {
            try {
                JSONArray velho = new JSONArray(prefs(c).getString("historico", "[]"));
                JSONArray h = new JSONArray();
                for (int i = 0; i < velho.length(); i++) {
                    if (!url.equals(velho.getJSONObject(i).optString("url"))) h.put(velho.getJSONObject(i));
                }
                prefs(c).edit().putString("historico", h.toString()).apply();
            } catch (Exception ignored) {
            }
        }
    }

    public static void renomear(Context c, String url, String titulo, String artista) {
        Cifra x = ler(c, url);
        if (x == null) return;
        x.titulo = titulo;
        x.artista = artista;
        if (ehMinha(c, url)) salvarMinha(c, x); else salvar(c, x);
    }

    /** A música identificada combina com esta cifra guardada? */
    static boolean combina(Cifra m, String artista, String titulo) {
        String t = Fonte.slug(Fonte.limparTitulo(titulo == null ? "" : titulo));
        if (t.isEmpty()) return false;
        String a = Fonte.slug(Fonte.limparArtista(artista == null ? "" : artista));
        String mt = Fonte.slug(m.titulo), ma = Fonte.slug(m.artista);
        String tudo = "-" + mt + "-" + ma + "-";
        if (!(t.equals(mt) || tudo.contains("-" + t + "-"))) return false;
        if (a.isEmpty() || ma.isEmpty()) return true;
        return ma.equals(a) || tudo.contains("-" + a + "-") || a.contains(ma) || ma.contains(a);
    }

    /** Procura primeiro nas minhas cifras, depois no histórico. */
    public static Cifra acharNoHistorico(Context c, String artista, String titulo) {
        // "Música - Artista" num campo só: testa as duas ordens
        List<String[]> pares = new ArrayList<>();
        pares.add(new String[]{artista, titulo});
        if ((artista == null || artista.isEmpty()) && titulo != null && titulo.contains(" - ")) {
            int k = titulo.indexOf(" - ");
            pares.add(new String[]{titulo.substring(0, k), titulo.substring(k + 3)});
            pares.add(new String[]{titulo.substring(k + 3), titulo.substring(0, k)});
        }
        for (String[] p : pares) {
            for (Cifra m : minhas(c)) {
                if (combina(m, p[0], p[1])) {
                    Cifra x = ler(c, m.url);
                    if (x != null) return x;
                }
            }
        }
        return acharSoNoHistorico(c, artista, titulo);
    }

    private static Cifra acharSoNoHistorico(Context c, String artista, String titulo) {
        String k = Cifra.chave(artista, titulo);
        for (Cifra h : historico(c)) {
            if (k.equals(Cifra.chave(h.artista, h.titulo))) return ler(c, h.url);
        }
        // também pelo endereço provável
        List<String> cand = Fonte.candidatos(artista, titulo);
        for (Cifra h : historico(c)) {
            if (cand.contains(h.url)) return ler(c, h.url);
        }
        return null;
    }

    // ---------- registro (para depurar o que o app capturou) ----------

    public static synchronized void registrar(Context c, String linha) {
        String hora = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.ROOT).format(new Date());
        String velho = prefs(c).getString("registro", "");
        String novo = hora + "  " + linha + "\n" + velho;
        if (novo.length() > 6000) novo = novo.substring(0, 6000);
        prefs(c).edit().putString("registro", novo).apply();
    }

    public static String registro(Context c) {
        String r = prefs(c).getString("registro", "");
        return r.isEmpty() ? "(vazio)" : r;
    }
}
