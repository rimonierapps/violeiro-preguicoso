package br.rimonier.violeiro;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pequenos atalhos para montar a tela em código (sem XML). */
public class Ui {
    public static final int FUNDO = 0xFF121212;
    public static final int CARTAO = 0xFF1E1E1E;
    public static final int TEXTO = 0xFFEDEDED;
    public static final int APAGADO = 0xFF9E9E9E;
    public static final int ACORDE = 0xFFFFA726;
    public static final int DESTAQUE = 0xFF2E7D32;

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static TextView texto(Context c, String s, float sp, int cor) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(cor);
        return t;
    }

    public static TextView titulo(Context c, String s) {
        TextView t = texto(c, s, 18, TEXTO);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 16), 0, dp(c, 6));
        return t;
    }

    public static Button botao(Context c, String s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(TEXTO);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF2C2C2C);
        g.setCornerRadius(dp(c, 10));
        b.setBackground(g);
        b.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        b.setMinHeight(dp(c, 46));
        b.setOnClickListener(l);
        return b;
    }

    public static Button botaoForte(Context c, String s, View.OnClickListener l) {
        Button b = botao(c, s, l);
        GradientDrawable g = new GradientDrawable();
        g.setColor(DESTAQUE);
        g.setCornerRadius(dp(c, 10));
        b.setBackground(g);
        return b;
    }

    /** Linha horizontal com os itens dividindo a largura igualmente. */
    public static LinearLayout linha(Context c, View... vs) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        for (View v : vs) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            p.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
            l.addView(v, p);
        }
        return l;
    }

    public static LinearLayout.LayoutParams larguraToda(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        return p;
    }
}
