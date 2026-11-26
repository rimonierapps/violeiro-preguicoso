package br.rimonier.violeiro;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Abordagem "do zero": grava alguns segundos pelo microfone e manda para um
 * serviço de reconhecimento de áudio, que devolve artista, título,
 * o ponto da música em que a gravação caiu (timecode) e a duração.
 */
public class Identificador {
    public static final int SEGUNDOS = 10;

    public interface Retorno {
        void status(String s);
        void ok(String artista, String titulo, Sinc sinc);
        void erro(String msg);
    }

    private static boolean ocupado;

    public static void ouvir(Activity a, Retorno r) {
        if (ocupado) { r.status("Já estou ouvindo…"); return; }
        if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
            r.erro("Permita o uso do microfone e toque de novo.");
            return;
        }
        File f = new File(a.getCacheDir(), "amostra.m4a");
        MediaRecorder rec = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(a) : new MediaRecorder();
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            rec.setAudioSamplingRate(44100);
            rec.setAudioEncodingBitRate(96000);
            rec.setAudioChannels(1);
            rec.setOutputFile(f.getAbsolutePath());
            rec.prepare();
            rec.start();
        } catch (Exception e) {
            rec.release();
            r.erro("Não consegui usar o microfone: " + e.getMessage());
            return;
        }
        ocupado = true;
        final long inicio = System.currentTimeMillis();
        r.status("🎤 Ouvindo por " + SEGUNDOS + " s… deixe o celular perto do som.");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
            r.status("Identificando…");
            new Thread(() -> {
                try {
                    JSONObject res = enviar(a, f);
                    a.runOnUiThread(() -> {
                        ocupado = false;
                        if (res == null) {
                            r.erro("Não reconheci a música. Tente com o som mais alto ou mais perto.");
                            return;
                        }
                        Sinc s = new Sinc();
                        s.t0 = inicio;
                        s.pos0 = timecode(res.optString("timecode"));
                        JSONObject sp = res.optJSONObject("spotify");
                        if (sp != null) s.dur = sp.optLong("duration_ms");
                        if (s.dur == 0) {
                            JSONObject am = res.optJSONObject("apple_music");
                            if (am != null) s.dur = am.optLong("durationInMillis");
                        }
                        Repositorio.registrar(a, "Reconhecimento: " + res.optString("artist") + " — " + res.optString("title")
                                + " (em " + res.optString("timecode") + ", duração " + s.dur / 1000 + " s)");
                        r.ok(res.optString("artist"), res.optString("title"), s);
                    });
                } catch (Exception e) {
                    a.runOnUiThread(() -> {
                        ocupado = false;
                        Repositorio.registrar(a, "Reconhecimento, erro: " + e.getMessage());
                        r.erro("Erro na identificação: " + e.getMessage());
                    });
                }
            }).start();
        }, SEGUNDOS * 1000L);
    }

    static long timecode(String tc) {
        if (tc == null || tc.isEmpty()) return 0;
        long ms = 0;
        for (String p : tc.trim().split(":")) {
            try { ms = ms * 60 + Long.parseLong(p.trim()); } catch (NumberFormatException e) { return 0; }
        }
        return ms * 1000;
    }

    /** Devolve o objeto "result" do serviço, ou null se não reconheceu. */
    static JSONObject enviar(Context c, File f) throws Exception {
        String token = Repositorio.prefs(c).getString("token_reconhecimento", "").trim();
        String b = "----violeiro" + System.nanoTime();
        HttpURLConnection con = (HttpURLConnection) new URL("https://api.audd.io/").openConnection();
        con.setDoOutput(true);
        con.setRequestMethod("POST");
        con.setConnectTimeout(15000);
        con.setReadTimeout(40000);
        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + b);
        try (OutputStream o = con.getOutputStream()) {
            if (!token.isEmpty()) campo(o, b, "api_token", token);
            campo(o, b, "return", "apple_music,spotify");
            o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"amostra.m4a\"\r\n"
                    + "Content-Type: audio/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            }
            o.write(("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
        }
        con.disconnect();
        String resp = out.toString(StandardCharsets.UTF_8.name());
        JSONObject j = new JSONObject(resp);
        if ("error".equals(j.optString("status"))) {
            JSONObject e = j.optJSONObject("error");
            String msg = e != null ? e.optString("error_message", resp) : resp;
            if (token.isEmpty()) msg += " (sem token: veja o README e coloque o token no botão ⚙️)";
            throw new Exception(msg);
        }
        return j.optJSONObject("result");
    }

    private static void campo(OutputStream o, String b, String nome, String valor) throws Exception {
        o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + nome + "\"\r\n\r\n" + valor + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }
}
