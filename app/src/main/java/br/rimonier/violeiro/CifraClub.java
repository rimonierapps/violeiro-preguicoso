package br.rimonier.violeiro;

import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Busca e leitura de cifras do Cifra Club.
 * O site não tem API pública: o app monta o endereço da música
 * (cifraclub.com.br/artista/musica/) e lê o bloco &lt;pre&gt; da página.
 */
public class CifraClub {
    public static final String BASE = "https://www.cifraclub.com.br/";
    static final String UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36";

    private static final Set<String> RESERVADOS = new HashSet<>(Arrays.asList(
            "letra", "blog", "academy", "aulas", "estilos", "top", "mais-acessadas", "pesquisa",
            "busca", "conta", "contribuicoes", "cifra-club-pro", "afinador", "dicionario-de-acordes",
            "metronomo", "videoaulas", "news", "artistas", "imprimir", "app", "ajuda", "cursos",
            "favoritos", "listas", "login", "perfil", "palco", "sobre", "privacidade", "termos"));

    // ---------- endereços ----------

    public static String slug(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        n = n.replace("&", " e ").replace("'", "").replace("’", "");
        n = n.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return n;
    }

    public static String limparTitulo(String t) {
        if (t == null) return "";
        String s = t.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", " ");
        s = s.replaceAll("(?i)\\s+-\\s+(.*(remaster|ao vivo|live|vers[aã]o|version|edit|mix|ac[uú]stic|acoustic|mono|stereo|single|radio|clipe|oficial|official|lyric|letra|\\d{4}).*)$", "");
        s = s.replaceAll("(?i)\\s+(feat\\.?|ft\\.?|part\\.?|participa[cç][aã]o)\\s+.*$", "");
        return s.trim();
    }

    public static String limparArtista(String a) {
        if (a == null) return "";
        String s = a.replaceAll("(?i)\\s*-\\s*topic$", "").replaceAll("(?i)vevo$", "");
        s = s.replaceAll("(?i)\\s+(feat\\.?|ft\\.?|part\\.?|participa[cç][aã]o)\\s+.*$", "");
        return s.trim();
    }

    /** Endereços prováveis para a música, do mais para o menos provável. */
    public static List<String> candidatos(String artista, String titulo) {
        List<String[]> pares = new ArrayList<>();
        artista = artista == null ? "" : artista.trim();
        titulo = titulo == null ? "" : titulo.trim();
        if (!artista.isEmpty()) pares.add(new String[]{artista, titulo});
        int i = titulo.indexOf(" - ");
        if (i > 0) {
            String a = titulo.substring(0, i).trim(), b = titulo.substring(i + 3).trim();
            pares.add(new String[]{a, b});
            pares.add(new String[]{b, a});
        }
        Set<String> urls = new LinkedHashSet<>();
        for (String[] p : pares) {
            List<String> artistas = new ArrayList<>();
            String la = limparArtista(p[0]);
            artistas.add(la);
            // "Fulano & Ciclano", "Fulano, Ciclano", "Fulano x Ciclano": tenta só o primeiro
            artistas.add(la.split("\\s*(,|&| x | X | e )\\s*")[0]);
            artistas.add(p[0]);
            for (String a : artistas) {
                for (String t : new String[]{limparTitulo(p[1]), p[1]}) {
                    String sa = slug(a), st = slug(t);
                    if (!sa.isEmpty() && !st.isEmpty()) urls.add(BASE + sa + "/" + st + "/");
                }
            }
        }
        return new ArrayList<>(urls);
    }

    /** Se o endereço for de uma página de música do Cifra Club, devolve ele normalizado. */
    public static String urlDeMusica(Uri u) {
        if (u == null || u.getHost() == null) return null;
        String host = u.getHost().toLowerCase(Locale.ROOT);
        if (!host.endsWith("cifraclub.com.br")) return null;
        List<String> seg = u.getPathSegments();
        if (seg.size() < 2 || seg.size() > 3) return null;
        String a = seg.get(0), m = seg.get(1);
        if (RESERVADOS.contains(a) || a.contains(".") || m.contains(".")) return null;
        if (seg.size() == 3 && !seg.get(2).endsWith(".html")) return null;
        return BASE + a + "/" + m + "/";
    }

    // ---------- rede ----------

    static String baixar(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9");
        try {
            int code = c.getResponseCode();
            if (code != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    /** Tenta os endereços prováveis. Devolve null se nenhum tiver cifra. */
    public static Cifra buscar(String artista, String titulo) throws IOException {
        IOException ultimoErro = null;
        boolean algumaResposta = false;
        for (String url : candidatos(artista, titulo)) {
            try {
                String html = baixar(url);
                algumaResposta = true;
                if (html == null) continue;
                Cifra c = ler(url, html);
                if (c != null) return c;
            } catch (IOException e) {
                ultimoErro = e;
            }
        }
        if (!algumaResposta && ultimoErro != null) throw ultimoErro;
        return null;
    }

    public static Cifra baixarCifra(String url) throws IOException {
        String html = baixar(url);
        return html == null ? null : ler(url, html);
    }

    // ---------- leitura da página ----------

    static Cifra ler(String url, String html) {
        int i = html.indexOf("<pre");
        if (i < 0) return null;
        int ini = html.indexOf('>', i) + 1;
        int fim = html.indexOf("</pre>", ini);
        if (ini <= 0 || fim < 0) return null;
        String pre = html.substring(ini, fim);
        pre = pre.replaceAll("(?is)<b(\\s[^>]*)?>(.*?)</b>", Cifra.INI + "$2" + Cifra.FIM);
        pre = pre.replaceAll("(?i)<br\\s*/?>", "\n");
        pre = pre.replaceAll("(?s)<[^>]+>", "");
        pre = entidades(pre).replace("\u200b", "").replace("\r", "");
        // tira acordes "vazios" e linhas em branco do começo/fim
        pre = pre.replace(Cifra.INI + "" + Cifra.FIM, "");
        pre = pre.replaceAll("^\\n+", "").replaceAll("\\s+$", "");
        if (pre.trim().isEmpty()) return null;

        Cifra c = new Cifra();
        c.url = url;
        c.texto = pre;
        // og:title = "Tempo Perdido - Legião Urbana - Cifra Club"
        Matcher m = Pattern.compile("property=\"og:title\"\\s+content=\"([^\"]*)\"").matcher(html);
        String og = m.find() ? entidades(m.group(1)) : "";
        if (og.isEmpty()) {
            Matcher t = Pattern.compile("(?is)<title>(.*?)</title>").matcher(html);
            if (t.find()) og = entidades(t.group(1)).trim();
        }
        og = og.replaceAll("\\s*-\\s*Cifra Club\\s*$", "");
        int k = og.lastIndexOf(" - ");
        if (k > 0) {
            c.titulo = og.substring(0, k).trim();
            c.artista = og.substring(k + 3).trim();
        } else {
            c.titulo = og;
        }
        return c;
    }

    /** &atilde; &eacute; &ccedil; etc. viram a letra acentuada. */
    static String acentuada(String e, String original) {
        String[][] marcas = {{"acute", "\u0301"}, {"grave", "\u0300"}, {"tilde", "\u0303"},
                {"circ", "\u0302"}, {"uml", "\u0308"}, {"cedil", "\u0327"}};
        for (String[] mk : marcas) {
            if (e.length() == mk[0].length() + 1 && e.endsWith(mk[0])) {
                return Normalizer.normalize(e.charAt(0) + mk[1], Normalizer.Form.NFC);
            }
        }
        return original;
    }

    static String entidades(String s) {
        Matcher m = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String e = m.group(1), r;
            try {
                if (e.startsWith("#x")) r = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                else if (e.startsWith("#")) r = new String(Character.toChars(Integer.parseInt(e.substring(1))));
                else {
                    switch (e) {
                        case "amp": r = "&"; break;
                        case "lt": r = "<"; break;
                        case "gt": r = ">"; break;
                        case "quot": r = "\""; break;
                        case "apos": r = "'"; break;
                        case "nbsp": r = " "; break;
                        default: r = acentuada(e, m.group(0));
                    }
                }
            } catch (Exception ex) {
                r = m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
