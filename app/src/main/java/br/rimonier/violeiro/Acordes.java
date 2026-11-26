package br.rimonier.violeiro;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transposição de tom. */
public class Acordes {
    private static final String[] SUS = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] BEM = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};
    private static final Pattern NOTA = Pattern.compile("(^|/)([A-G])([#b]?)");

    static int indice(String nota) {
        for (int i = 0; i < 12; i++) if (SUS[i].equals(nota) || BEM[i].equals(nota)) return i;
        switch (nota) { // casos raros
            case "Cb": return 11;
            case "Fb": return 4;
            case "E#": return 5;
            case "B#": return 0;
        }
        return -1;
    }

    public static String transpor(String acorde, int semitons) {
        int s = ((semitons % 12) + 12) % 12;
        if (s == 0) return acorde;
        boolean bemol = acorde.matches("^[A-G]b.*");
        Matcher m = NOTA.matcher(acorde);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int i = indice(m.group(2) + m.group(3));
            String nova = i < 0 ? m.group(2) + m.group(3) : (bemol ? BEM : SUS)[(i + s) % 12];
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + nova));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Transpõe todos os acordes marcados no texto, tentando manter o alinhamento
     * com a letra: se o acorde cresce, come espaços seguintes; se diminui, completa com espaços.
     */
    public static String transporTexto(String t, int semitons) {
        if (((semitons % 12) + 12) % 12 == 0) return t;
        StringBuilder out = new StringBuilder(t.length() + 64);
        int i = 0, n = t.length(), divida = 0;
        while (i < n) {
            char c = t.charAt(i);
            if (c == Cifra.INI) {
                int e = t.indexOf(Cifra.FIM, i);
                if (e < 0) { out.append(t.substring(i)); break; }
                String ac = t.substring(i + 1, e);
                String novo = transpor(ac, semitons);
                out.append(Cifra.INI).append(novo).append(Cifra.FIM);
                divida += novo.length() - ac.length();
                if (divida < 0) {
                    for (int k = 0; k < -divida; k++) out.append(' ');
                    divida = 0;
                }
                i = e + 1;
                continue;
            }
            if (c == '\n') divida = 0;
            else if (c == ' ' && divida > 0 && i + 1 < n && t.charAt(i + 1) == ' ') {
                divida--;
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static final Pattern ACORDE = Pattern.compile(
            "^\\(?[A-G][#b]?(?:maj|min|dim|aug|sus|add|[mM]|º|°|\\+|-|[0-9]|\\(|\\)|#|b|/(?=[0-9]))*(?:/[A-G][#b]?)?\\)?$");
    private static final Pattern IGNORAVEL = Pattern.compile("^(\\[.*\\]|\\|+|x\\d+|\\d+x|-+|\\(?x?\\d+\\)?|%|\\.+)$",
            Pattern.CASE_INSENSITIVE);

    public static boolean ehAcorde(String token) {
        return ACORDE.matcher(token).matches();
    }

    /**
     * Para cifras coladas ou importadas: acha as linhas que só têm acordes
     * e marca cada acorde (para destacar e mudar o tom).
     */
    public static String marcarAcordes(String texto) {
        StringBuilder out = new StringBuilder(texto.length() + 256);
        String[] linhas = texto.split("\n", -1);
        for (int li = 0; li < linhas.length; li++) {
            String l = linhas[li];
            if (li > 0) out.append('\n');
            String[] toks = l.trim().split("\\s+");
            int acordes = 0;
            boolean soAcordes = !l.trim().isEmpty();
            for (String t : toks) {
                if (t.isEmpty()) continue;
                if (ehAcorde(t)) acordes++;
                else if (!IGNORAVEL.matcher(t).matches()) { soAcordes = false; break; }
            }
            if (!soAcordes || acordes == 0) { out.append(l); continue; }
            // marca mantendo as posições
            Matcher m = Pattern.compile("\\S+").matcher(l);
            int fim = 0;
            while (m.find()) {
                out.append(l, fim, m.start());
                String t = m.group();
                if (ehAcorde(t)) {
                    String pre = "", pos = "";
                    if (t.startsWith("(")) { pre = "("; t = t.substring(1); }
                    long abre = t.chars().filter(ch -> ch == '(').count(), fecha = t.chars().filter(ch -> ch == ')').count();
                    if (t.endsWith(")") && fecha > abre) { pos = ")"; t = t.substring(0, t.length() - 1); }
                    out.append(pre).append(Cifra.INI).append(t).append(Cifra.FIM).append(pos);
                } else {
                    out.append(t);
                }
                fim = m.end();
            }
            out.append(l.substring(fim));
        }
        return out.toString();
    }

    public static String nomeTom(int semitons) {
        if (semitons == 0) return "original";
        return (semitons > 0 ? "+" : "") + semitons;
    }
}
