package br.rimonier.violeiro;

import android.content.Intent;

/** Onde a música está agora: duração, posição num instante conhecido e se está tocando. */
public class Sinc {
    public long dur;      // duração total (ms), 0 = desconhecida
    public long pos0;     // posição (ms) no instante t0
    public long t0;       // System.currentTimeMillis() em que pos0 valia
    public boolean tocando = true;
    public boolean player; // veio de um player do próprio celular (segue ao vivo)

    public boolean temDuracao() {
        return dur > 0;
    }

    public long posicaoAgora() {
        if (t0 == 0) return pos0;
        return tocando ? pos0 + (System.currentTimeMillis() - t0) : pos0;
    }

    public void colocar(Intent i) {
        i.putExtra("s_dur", dur);
        i.putExtra("s_pos0", pos0);
        i.putExtra("s_t0", t0);
        i.putExtra("s_tocando", tocando);
        i.putExtra("s_player", player);
    }

    public static Sinc de(Intent i) {
        if (i == null || !i.hasExtra("s_t0")) return null;
        Sinc s = new Sinc();
        s.dur = i.getLongExtra("s_dur", 0);
        s.pos0 = i.getLongExtra("s_pos0", 0);
        s.t0 = i.getLongExtra("s_t0", 0);
        s.tocando = i.getBooleanExtra("s_tocando", true);
        s.player = i.getBooleanExtra("s_player", false);
        return s;
    }
}
