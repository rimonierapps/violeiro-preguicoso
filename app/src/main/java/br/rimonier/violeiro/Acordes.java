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

    public static String nomeTom(int semitons) {
        if (semitons == 0) return "original";
        return (semitons > 0 ? "+" : "") + semitons;
    }
}
