package br.rimonier.violeiro;

import org.json.JSONException;
import org.json.JSONObject;

/** Uma cifra baixada. Os acordes ficam marcados no texto entre \u0001 e \u0002. */
public class Cifra {
    public static final char INI = '\u0001';
    public static final char FIM = '\u0002';

    public String url = "";
    public String titulo = "";
    public String artista = "";
    public String texto = "";

    public JSONObject paraJson(boolean comTexto) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("url", url);
        j.put("titulo", titulo);
        j.put("artista", artista);
        if (comTexto) j.put("texto", texto);
        return j;
    }

    public static Cifra deJson(JSONObject j) {
        Cifra c = new Cifra();
        c.url = j.optString("url");
        c.titulo = j.optString("titulo");
        c.artista = j.optString("artista");
        c.texto = j.optString("texto");
        return c;
    }

    /** Chave usada para comparar se duas detecções são a mesma música. */
    public static String chave(String artista, String titulo) {
        return Fonte.slug(Fonte.limparArtista(artista == null ? "" : artista)) + "|"
                + Fonte.slug(Fonte.limparTitulo(titulo == null ? "" : titulo));
    }
}
