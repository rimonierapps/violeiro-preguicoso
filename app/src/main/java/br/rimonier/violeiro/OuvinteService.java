package br.rimonier.violeiro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Abordagem "integração": lê
 *  1) as notificações de resultado dos apps de identificação de música;
 *  2) o que está tocando nos players do celular,
 *     com a posição exata da música — dá para rolar a cifra no tempo certo.
 */
public class OuvinteService extends NotificationListenerService {
    public static final String ACAO = "br.rimonier.violeiro.MUSICA";
    static final String[] IDENTIFICADORES_ORDEM = {
            "com.shazam.android",
            "com.melodis.midomiMusicIdentifier.freemium",
            "com.melodis.midomiMusicIdentifier",
            "com.google.intelligence.sense"
    };
    static final Set<String> IDENTIFICADORES = new HashSet<>(Arrays.asList(IDENTIFICADORES_ORDEM));

    /** Quantas telas do app estão visíveis (para decidir entre abrir direto ou notificar). */
    public static int telasVisiveis = 0;

    private MediaSessionManager msm;
    private ComponentName cn;
    private final List<MediaController> controles = new ArrayList<>();
    private final List<MediaController.Callback> callbacks = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private String ultimaChave = "";

    private final MediaSessionManager.OnActiveSessionsChangedListener mudou = this::acompanhar;

    public static boolean permitido(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    @Override
    public void onListenerConnected() {
        Repositorio.registrar(this, "Leitor de notificações conectado");
        cn = new ComponentName(this, OuvinteService.class);
        msm = getSystemService(MediaSessionManager.class);
        try {
            msm.addOnActiveSessionsChangedListener(mudou, cn);
            acompanhar(msm.getActiveSessions(cn));
        } catch (SecurityException e) {
            Repositorio.registrar(this, "Sem acesso aos players: " + e.getMessage());
        }
    }

    @Override
    public void onListenerDisconnected() {
        Repositorio.registrar(this, "Leitor de notificações desconectado");
        if (msm != null) {
            try { msm.removeOnActiveSessionsChangedListener(mudou); } catch (Exception ignored) { }
        }
        soltar();
    }

    private void soltar() {
        for (int i = 0; i < controles.size(); i++) {
            try { controles.get(i).unregisterCallback(callbacks.get(i)); } catch (Exception ignored) { }
        }
        controles.clear();
        callbacks.clear();
    }

    private void acompanhar(List<MediaController> lista) {
        soltar();
        if (lista == null) return;
        for (MediaController c : lista) {
            if (getPackageName().equals(c.getPackageName())) continue;
            MediaController.Callback cb = new MediaController.Callback() {
                @Override public void onMetadataChanged(MediaMetadata m) { deMidia(c); }
                @Override public void onPlaybackStateChanged(PlaybackState s) { deMidia(c); }
            };
            c.registerCallback(cb, h);
            controles.add(c);
            callbacks.add(cb);
            deMidia(c);
        }
    }

    private void deMidia(MediaController c) {
        if (!Repositorio.prefs(this).getBoolean("seguir_player", true)) return;
        MediaMetadata m = c.getMetadata();
        PlaybackState s = c.getPlaybackState();
        if (m == null) return;
        String titulo = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artista = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artista == null || artista.isEmpty()) artista = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (titulo == null || titulo.isEmpty()) return;
        Sinc sinc = new Sinc();
        sinc.player = true;
        sinc.dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        sinc.tocando = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        long pos = 0;
        if (s != null) {
            pos = s.getPosition();
            if (sinc.tocando) {
                pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
            }
        }
        sinc.pos0 = Math.max(0, pos);
        sinc.t0 = System.currentTimeMillis();
        avisar("player " + c.getPackageName(), artista == null ? "" : artista, titulo, sinc);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!IDENTIFICADORES.contains(pkg)) return;
        Notification n = sbn.getNotification();
        Bundle ex = n.extras;
        CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = ex.getCharSequence(Notification.EXTRA_TEXT);
        boolean fixa = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        Repositorio.registrar(this, "Notificação " + pkg + ": «" + t + "» / «" + x + "»" + (fixa ? " [fixa]" : ""));
        if (!Repositorio.prefs(this).getBoolean("seguir_identificador", true)) return;
        if (fixa || t == null || x == null) return;
        String titulo = t.toString().trim(), artista = x.toString().trim();
        String tudo = (titulo + " " + artista).toLowerCase();
        for (String status : new String[]{"shazam", "soundhound", "ouvindo", "listening", "toque para", "tap to",
                "procurando", "searching", "nenhum resultado", "no result", "não encontr", "tente novamente"}) {
            if (tudo.contains(status)) return;
        }
        Sinc sinc = new Sinc(); // sem posição: a notificação não informa
        sinc.t0 = System.currentTimeMillis();
        avisar(pkg, artista, titulo, sinc);
    }

    private void avisar(String fonte, String artista, String titulo, Sinc sinc) {
        String chave = Cifra.chave(artista, titulo);
        boolean nova = !chave.equals(ultimaChave);
        if (nova) {
            ultimaChave = chave;
            Repositorio.registrar(this, "Detectada (" + fonte + "): " + artista + " — " + titulo);
        }
        if (telasVisiveis > 0) {
            Intent b = new Intent(ACAO).setPackage(getPackageName());
            b.putExtra("artista", artista);
            b.putExtra("musica", titulo);
            sinc.colocar(b);
            sendBroadcast(b);
        } else if (nova && sinc.tocando && Repositorio.prefs(this).getBoolean("notificar", true)) {
            notificar(artista, titulo, sinc);
        }
    }

    private void notificar(String artista, String titulo, Sinc sinc) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("cifras", "Cifras detectadas",
                NotificationManager.IMPORTANCE_HIGH));
        Intent i = new Intent(this, CifraActivity.class);
        i.putExtra("artista", artista);
        i.putExtra("musica", titulo);
        sinc.colocar(i);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "cifras")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("🎸 " + titulo)
                .setContentText(artista + " — toque para abrir a cifra")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        try { nm.notify(1, n); } catch (SecurityException ignored) { }
    }
}
