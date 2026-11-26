package br.rimonier.violeiro;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Traz para o app suas cifras: texto colado, HTML, TXT, PDF e listas de músicas. */
public class Importador {

    static String novoId() {
        return "local:" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
    }

    // ---------- texto ----------

    public static Cifra deTexto(String titulo, String artista, String texto) {
        Cifra c = new Cifra();
        c.url = novoId();
        c.titulo = titulo == null || titulo.trim().isEmpty() ? "Sem título" : titulo.trim();
        c.artista = artista == null ? "" : artista.trim();
        String t = texto.replace("\r", "").replace("\t", "    ").replace("​", "").replace(' ', ' ');
        t = t.replaceAll("^\\n+", "").replaceAll("\\s+$", "");
        c.texto = t.indexOf(Cifra.INI) >= 0 ? t : Acordes.marcarAcordes(t);
        return c;
    }

    /** Tenta achar título/artista nas primeiras linhas do texto colado. */
    public static String[] adivinharNome(String texto) {
        for (String l : texto.split("\n")) {
            String s = l.trim();
            if (s.isEmpty() || s.startsWith("[")) continue;
            boolean temAcorde = false;
            for (String t : s.split("\\s+")) if (Acordes.ehAcorde(t)) { temAcorde = true; break; }
            if (temAcorde && s.split("\\s+").length < 6) continue;
            if (s.length() > 60) break;
            return separar(s);
        }
        return new String[]{"", ""};
    }

    // ---------- HTML ----------

    public static Cifra deHtml(String html, String nomeArquivo) {
        // página salva do próprio site de cifras: usa a leitura completa
        if (html.contains(Fonte.SUFIXO_TITULO) && html.contains("<pre")) {
            Cifra c = Fonte.ler(novoId(), html);
            if (c != null) {
                if (c.titulo.isEmpty()) c.titulo = semExtensao(nomeArquivo);
                return c;
            }
        }
        String corpo = html;
        int p = html.indexOf("<pre");
        if (p >= 0) {
            int ini = html.indexOf('>', p) + 1, fim = html.indexOf("</pre>", ini);
            if (ini > 0 && fim > ini) corpo = html.substring(ini, fim);
        } else {
            corpo = corpo.replaceAll("(?is)<(script|style|head|nav|footer)[^>]*>.*?</\\1>", "");
            corpo = corpo.replaceAll("(?i)</(p|div|li|h[1-6]|tr)>", "\n");
        }
        corpo = corpo.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?s)<[^>]+>", "");
        corpo = Fonte.entidades(corpo).replaceAll("\\n{3,}", "\n\n");

        String titulo = "";
        Matcher m = Pattern.compile("(?is)<title>(.*?)</title>").matcher(html);
        if (m.find()) titulo = Fonte.entidades(m.group(1)).trim();
        if (titulo.isEmpty()) titulo = semExtensao(nomeArquivo);
        String[] ta = separar(titulo);
        return deTexto(ta[0], ta[1], corpo);
    }

    // ---------- arquivos ----------

    public static String nome(Context c, Uri uri) {
        String n = null;
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) n = cur.getString(0);
        } catch (Exception ignored) {
        }
        if (n == null) n = uri.getLastPathSegment();
        return n == null ? "cifra" : n;
    }

    static byte[] ler(Context c, Uri uri) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("não consegui abrir o arquivo");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    static String semExtensao(String n) {
        if (n == null) return "";
        int k = n.lastIndexOf('.');
        return (k > 0 ? n.substring(0, k) : n).replace('_', ' ').trim();
    }

    /** "Artista - Música" → {Música, Artista}; sem separador → {nome, ""}. */
    static String[] separar(String s) {
        s = s.replaceAll("\\s*[-|–]\\s*(Cifra|Cifras|Letra|Tab)[^-|–]*$", "").trim();
        int k = s.indexOf(" - ");
        if (k < 0) k = s.indexOf(" – ");
        if (k > 0) return new String[]{s.substring(k + 3).trim(), s.substring(0, k).trim()};
        return new String[]{s, ""};
    }

    /** Importa um arquivo (PDF, HTML ou texto). Devolve null se o tipo não for aceito. */
    public static Cifra deArquivo(Context c, Uri uri, String tipo) throws IOException {
        String nome = nome(c, uri);
        String ext = nome.toLowerCase(Locale.ROOT);
        tipo = tipo == null ? "" : tipo.toLowerCase(Locale.ROOT);
        byte[] dados = ler(c, uri);
        boolean pdf = tipo.contains("pdf") || ext.endsWith(".pdf")
                || (dados.length > 4 && dados[0] == '%' && dados[1] == 'P' && dados[2] == 'D' && dados[3] == 'F');
        if (pdf) {
            Cifra x = new Cifra();
            x.url = novoId();
            String[] ta = separar(semExtensao(nome));
            x.titulo = ta[0];
            x.artista = ta[1];
            File d = new File(c.getFilesDir(), "pdfs");
            d.mkdirs();
            File f = new File(d, Fonte.slug(x.url) + ".pdf");
            try (OutputStream o = new FileOutputStream(f)) {
                o.write(dados);
            }
            x.pdf = f.getAbsolutePath();
            return x;
        }
        String txt = new String(dados, StandardCharsets.UTF_8);
        if (txt.indexOf('�') >= 0) txt = new String(dados, StandardCharsets.ISO_8859_1); // arquivos antigos do Windows
        if (tipo.contains("html") || ext.endsWith(".html") || ext.endsWith(".htm")
                || txt.trim().toLowerCase(Locale.ROOT).startsWith("<!doctype html") || txt.contains("<html")) {
            return deHtml(txt, nome);
        }
        if (tipo.startsWith("text") || ext.endsWith(".txt") || ext.endsWith(".chordpro") || ext.endsWith(".cho")
                || ext.endsWith(".md") || tipo.isEmpty()) {
            String[] ta = separar(semExtensao(nome));
            return deTexto(ta[0], ta[1], txt);
        }
        return null;
    }

    // ---------- lista de músicas ----------

    /**
     * Uma música por linha. Aceita "Artista - Música", "Música;Artista", "Música, Artista", tabulação.
     * Devolve pares {artista, música}; quando a ordem é incerta, vai tudo em "música" e a busca testa as duas.
     */
    public static List<String[]> lista(String texto) {
        List<String[]> l = new ArrayList<>();
        for (String linha : texto.split("\n")) {
            String s = linha.replaceAll("^\\s*(\\d+[.)-]|[-*•])\\s*", "").trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            s = s.replaceAll("\\s*[;\\t]\\s*", " - ");
            if (!s.contains(" - ") && s.split(",").length == 2) s = s.replace(",", " - ");
            l.add(new String[]{"", s.replaceAll("\\s+", " ")});
        }
        return l;
    }
}
