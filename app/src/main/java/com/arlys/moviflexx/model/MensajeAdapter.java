package com.arlys.moviflexx.model;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MensajeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TIPO_PROPIO         = 1;
    private static final int TIPO_RECIBIDO       = 2;
    private static final int TIPO_AUDIO_PROPIO   = 3;
    private static final int TIPO_AUDIO_RECIBIDO = 4;

    // Colores ticks estilo WhatsApp
    private static final int COLOR_TICK_GRIS = 0xFF9E9E9E;  // gris — enviado/entregado
    private static final int COLOR_TICK_TEAL = 0xFF1AB99F;  // teal — leído por el contacto
    private static final int COLOR_HORA_SENT = 0xFF4DA89F;
    private static final int COLOR_HORA_RECV = 0xFF99B8B4;

    public interface OnMensajeVistoListener {
        void onVisto(Mensaje mensaje);
    }

    private final List<Mensaje> lista;
    private final int           idUsuarioActual;
    private OnMensajeVistoListener vistoListener;

    // ── Reproductor único activo ──────────────────────────────────────────────
    private MediaPlayer mediaPlayerActivo = null;
    private VHAudio     vhActivo          = null;
    private String      rutaActiva        = null;
    private final Handler  progressHandler  = new Handler(Looper.getMainLooper());
    private Runnable       progressRunnable = null;

    public MensajeAdapter(List<Mensaje> lista, int idUsuarioActual) {
        this.lista           = lista;
        this.idUsuarioActual = idUsuarioActual;
    }

    public void setOnMensajeVistoListener(OnMensajeVistoListener l) {
        this.vistoListener = l;
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public int getItemViewType(int position) {
        Mensaje m = lista.get(position);
        if (m.isTipoAudio()) {
            return m.isEsPropio() ? TIPO_AUDIO_PROPIO : TIPO_AUDIO_RECIBIDO;
        }
        return m.isEsPropio() ? TIPO_PROPIO : TIPO_RECIBIDO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TIPO_AUDIO_PROPIO:
                return new VHAudio(inf.inflate(R.layout.item_mensaje_audio_propio, parent, false), true);
            case TIPO_AUDIO_RECIBIDO:
                return new VHAudio(inf.inflate(R.layout.item_mensaje_audio_recibido, parent, false), false);
            case TIPO_PROPIO:
                return new VHPropio(inf.inflate(R.layout.item_mensaje_propio, parent, false));
            default:
                return new VHRecibido(inf.inflate(R.layout.item_mensaje_recibido, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Mensaje m        = lista.get(position);
        Mensaje anterior = position > 0 ? lista.get(position - 1) : null;
        boolean mostrarFecha = esDiferente(anterior, m);

        if (holder instanceof VHAudio) {
            bindAudio((VHAudio) holder, m, mostrarFecha);
        } else if (holder instanceof VHPropio) {
            bindPropio((VHPropio) holder, m, mostrarFecha);
        } else {
            bindRecibido((VHRecibido) holder, m, mostrarFecha);
        }
    }

    @Override
    public int getItemCount() { return lista.size(); }

    // ─────────────────────────────────────────────────────────────────────────
    //  BIND AUDIO
    // ─────────────────────────────────────────────────────────────────────────
    private void bindAudio(VHAudio h, Mensaje m, boolean mostrarFecha) {
        if (h.tvFechaHeader != null) {
            h.tvFechaHeader.setVisibility(mostrarFecha ? View.VISIBLE : View.GONE);
            if (mostrarFecha) h.tvFechaHeader.setText(formatearHeaderFecha(m.getFechaEnvio()));
        }
        h.tvHora.setText(formatearHora(m.getFechaEnvio()));
        h.tvDuracion.setText(extraerDuracion(m.getContenido()));

        if (h.esPropio && h.tvTicks != null) {
            aplicarTicks(h.tvTicks, h.pbEnviando, m);
        }

        String ruta = m.getRutaAudioLocal();
        boolean archivoDisponible = ruta != null && !ruta.isEmpty() && new File(ruta).exists();

        if (vhActivo == h && mediaPlayerActivo != null) {
            h.btnPlayPause.setImageResource(
                    mediaPlayerActivo.isPlaying()
                            ? android.R.drawable.ic_media_pause
                            : android.R.drawable.ic_media_play);
        } else {
            h.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            h.seekBar.setProgress(0);
            h.tvDuracion.setText(extraerDuracion(m.getContenido()));
        }

        h.btnPlayPause.setAlpha(archivoDisponible ? 1f : 0.45f);
        h.btnPlayPause.setOnClickListener(v -> {
            if (!archivoDisponible) return;
            toggleReproduccion(h, m, ruta);
        });

        h.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && vhActivo == h && mediaPlayerActivo != null) {
                    mediaPlayerActivo.seekTo((int) (progress / 100f * mediaPlayerActivo.getDuration()));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LÓGICA REPRODUCCIÓN
    // ─────────────────────────────────────────────────────────────────────────
    private void toggleReproduccion(VHAudio h, Mensaje m, String ruta) {
        if (vhActivo != null && vhActivo != h) {
            pararReproductor(true);
        }
        if (vhActivo == h && mediaPlayerActivo != null) {
            if (mediaPlayerActivo.isPlaying()) {
                mediaPlayerActivo.pause();
                h.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                pararProgressRunnable();
            } else {
                mediaPlayerActivo.start();
                h.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                iniciarProgressRunnable(h, m);
            }
        } else {
            try {
                mediaPlayerActivo = new MediaPlayer();
                mediaPlayerActivo.setDataSource(ruta);
                mediaPlayerActivo.prepare();
                mediaPlayerActivo.start();
                vhActivo   = h;
                rutaActiva = ruta;
                h.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                h.tvDuracion.setText(formatearSegundos(mediaPlayerActivo.getDuration() / 1000));
                iniciarProgressRunnable(h, m);
                mediaPlayerActivo.setOnCompletionListener(mp -> {
                    h.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                    h.seekBar.setProgress(0);
                    h.tvDuracion.setText(extraerDuracion(m.getContenido()));
                    pararProgressRunnable();
                    mp.release();
                    mediaPlayerActivo = null;
                    vhActivo          = null;
                    rutaActiva        = null;
                });
            } catch (Exception e) {
                e.printStackTrace();
                h.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
        }
    }

    private void pararReproductor(boolean resetUI) {
        pararProgressRunnable();
        if (resetUI && vhActivo != null) {
            vhActivo.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            vhActivo.seekBar.setProgress(0);
        }
        if (mediaPlayerActivo != null) {
            try { if (mediaPlayerActivo.isPlaying()) mediaPlayerActivo.stop(); } catch (Exception ignored) {}
            mediaPlayerActivo.release();
            mediaPlayerActivo = null;
        }
        vhActivo   = null;
        rutaActiva = null;
    }

    private void iniciarProgressRunnable(VHAudio h, Mensaje m) {
        pararProgressRunnable();
        progressRunnable = new Runnable() {
            @Override public void run() {
                if (mediaPlayerActivo == null || vhActivo != h) return;
                try {
                    int dur  = mediaPlayerActivo.getDuration();
                    int curr = mediaPlayerActivo.getCurrentPosition();
                    if (dur > 0) {
                        h.seekBar.setProgress((int) (curr * 100f / dur));
                        h.tvDuracion.setText(formatearSegundos(curr / 1000));
                    }
                    if (mediaPlayerActivo.isPlaying()) {
                        progressHandler.postDelayed(this, 100);
                    }
                } catch (Exception ignored) {}
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void pararProgressRunnable() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BIND PROPIO
    // ─────────────────────────────────────────────────────────────────────────
    private void bindPropio(VHPropio h, Mensaje m, boolean mostrarFecha) {
        if (h.tvFechaHeader != null) {
            h.tvFechaHeader.setVisibility(mostrarFecha ? View.VISIBLE : View.GONE);
            if (mostrarFecha) h.tvFechaHeader.setText(formatearHeaderFecha(m.getFechaEnvio()));
        }
        h.tvContenido.setText(m.getContenido() != null ? m.getContenido() : "");
        h.tvHora.setText(formatearHora(m.getFechaEnvio()));
        h.tvHora.setTextColor(COLOR_HORA_SENT);
        aplicarTicks(h.tvTicks, h.pbEnviando, m);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BIND RECIBIDO
    // ─────────────────────────────────────────────────────────────────────────
    private void bindRecibido(VHRecibido h, Mensaje m, boolean mostrarFecha) {
        if (h.tvFechaHeader != null) {
            h.tvFechaHeader.setVisibility(mostrarFecha ? View.VISIBLE : View.GONE);
            if (mostrarFecha) h.tvFechaHeader.setText(formatearHeaderFecha(m.getFechaEnvio()));
        }
        h.tvContenido.setText(m.getContenido() != null ? m.getContenido() : "");
        h.tvHora.setText(formatearHora(m.getFechaEnvio()));
        h.tvHora.setTextColor(COLOR_HORA_RECV);

        if (vistoListener != null && !m.isLeido() && m.getId() > 0) {
            vistoListener.onVisto(m);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TICKS ESTILO WHATSAPP
    //  ✓  gris  = enviado localmente (id negativo, aún no confirmado)
    //  ✓✓ gris  = entregado al servidor, contacto NO lo ha leído
    //  ✓✓ teal  = contacto ya lo leyó (leido=true viene del backend)
    //  ⚠  rojo  = falló el envío
    //  ⏱  gris  = enviando...
    // ─────────────────────────────────────────────────────────────────────────
    private void aplicarTicks(TextView tvTicks, ProgressBar pb, Mensaje m) {
        if (tvTicks == null) return;

        if (m.isEnviando()) {
            // Enviando — mostrar spinner, ocultar ticks
            tvTicks.setVisibility(View.GONE);
            if (pb != null) pb.setVisibility(View.VISIBLE);

        } else if (m.isFallido()) {
            // Error de envío
            if (pb != null) pb.setVisibility(View.GONE);
            tvTicks.setVisibility(View.VISIBLE);
            tvTicks.setText("⚠");
            tvTicks.setTextColor(0xFFE53935);

        } else if (m.isLeido()) {
            // Leído por el contacto → ✓✓ teal
            if (pb != null) pb.setVisibility(View.GONE);
            tvTicks.setVisibility(View.VISIBLE);
            tvTicks.setText("✓✓");
            tvTicks.setTextColor(COLOR_TICK_TEAL);

        } else if (m.getId() > 0) {
            // Entregado al servidor pero no leído → ✓✓ gris
            if (pb != null) pb.setVisibility(View.GONE);
            tvTicks.setVisibility(View.VISIBLE);
            tvTicks.setText("✓✓");
            tvTicks.setTextColor(COLOR_TICK_GRIS);

        } else {
            // ID negativo = mensaje optimista local = ✓ gris simple
            if (pb != null) pb.setVisibility(View.GONE);
            tvTicks.setVisibility(View.VISIBLE);
            tvTicks.setText("✓");
            tvTicks.setTextColor(COLOR_TICK_GRIS);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW HOLDERS
    // ─────────────────────────────────────────────────────────────────────────
    static class VHAudio extends RecyclerView.ViewHolder {
        TextView    tvFechaHeader, tvHora, tvDuracion, tvTicks;
        ImageButton btnPlayPause;
        SeekBar     seekBar;
        ProgressBar pbEnviando;
        boolean     esPropio;

        VHAudio(@NonNull View v, boolean esPropio) {
            super(v);
            this.esPropio  = esPropio;
            tvFechaHeader  = v.findViewById(R.id.tvFechaHeader);
            tvHora         = v.findViewById(R.id.tvHora);
            tvDuracion     = v.findViewById(R.id.tvDuracionAudio);
            btnPlayPause   = v.findViewById(R.id.btnPlayPause);
            seekBar        = v.findViewById(R.id.seekBarAudio);
            tvTicks        = esPropio ? v.findViewById(R.id.tvTicks)    : null;
            pbEnviando     = esPropio ? v.findViewById(R.id.pbEnviando) : null;
        }
    }

    static class VHPropio extends RecyclerView.ViewHolder {
        TextView    tvFechaHeader, tvContenido, tvHora, tvTicks;
        ProgressBar pbEnviando;

        VHPropio(@NonNull View v) {
            super(v);
            tvFechaHeader = v.findViewById(R.id.tvFechaHeader);
            tvContenido   = v.findViewById(R.id.tvContenido);
            tvHora        = v.findViewById(R.id.tvHora);
            tvTicks       = v.findViewById(R.id.tvTicks);
            pbEnviando    = v.findViewById(R.id.pbEnviando);
        }
    }

    static class VHRecibido extends RecyclerView.ViewHolder {
        TextView tvFechaHeader, tvContenido, tvHora;

        VHRecibido(@NonNull View v) {
            super(v);
            tvFechaHeader = v.findViewById(R.id.tvFechaHeader);
            tvContenido   = v.findViewById(R.id.tvContenido);
            tvHora        = v.findViewById(R.id.tvHora);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private String extraerDuracion(String contenido) {
        if (contenido == null) return "0:00";
        try {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("\\((\\d+)s\\)").matcher(contenido);
            if (m.find()) return formatearSegundos(Integer.parseInt(m.group(1)));
            m = java.util.regex.Pattern.compile("\\((\\d+)m (\\d+)s\\)").matcher(contenido);
            if (m.find()) {
                int min = Integer.parseInt(m.group(1));
                int seg = Integer.parseInt(m.group(2));
                return min + ":" + String.format(Locale.getDefault(), "%02d", seg);
            }
        } catch (Exception ignored) {}
        return "0:00";
    }

    private String formatearSegundos(int total) {
        return (total / 60) + ":" + String.format(Locale.getDefault(), "%02d", total % 60);
    }

    private boolean esDiferente(Mensaje anterior, Mensaje actual) {
        if (anterior == null) return true;
        long tsAnt = parsearTimestamp(anterior.getFechaEnvio());
        long tsAct = parsearTimestamp(actual.getFechaEnvio());
        if (tsAnt == 0 || tsAct == 0) return false;
        Calendar cAnt = Calendar.getInstance(); cAnt.setTimeInMillis(tsAnt);
        Calendar cAct = Calendar.getInstance(); cAct.setTimeInMillis(tsAct);
        return cAnt.get(Calendar.DAY_OF_YEAR) != cAct.get(Calendar.DAY_OF_YEAR)
                || cAnt.get(Calendar.YEAR) != cAct.get(Calendar.YEAR);
    }

    private String formatearHeaderFecha(String fechaStr) {
        long ts = parsearTimestamp(fechaStr);
        if (ts == 0) return "";
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("America/Bogota");
        Calendar hoy   = Calendar.getInstance(tz);
        Calendar fecha = Calendar.getInstance(tz);
        fecha.setTimeInMillis(ts);
        if (esMismoDia(hoy, fecha)) return "Hoy";
        hoy.add(Calendar.DAY_OF_YEAR, -1);
        if (esMismoDia(hoy, fecha)) return "Ayer";
        Calendar inicioSemana = Calendar.getInstance(tz);
        inicioSemana.add(Calendar.DAY_OF_YEAR, -6);
        if (fecha.after(inicioSemana)) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM", new Locale("es", "CO"));
            sdf.setTimeZone(tz);
            return sdf.format(new Date(ts));
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        sdf.setTimeZone(tz);
        return sdf.format(new Date(ts));
    }

    private boolean esMismoDia(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private String formatearHora(String fechaStr) {
        if (fechaStr == null || fechaStr.isEmpty()) return "";
        long ts = parsearTimestamp(fechaStr);
        if (ts == 0) return "";
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("America/Bogota");
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        sdf.setTimeZone(tz);
        return sdf.format(new Date(ts));
    }

    private long parsearTimestamp(String fecha) {
        if (fecha == null || fecha.isEmpty()) return 0L;
        // Si es número directo (timestamp unix)
        try { long n = Long.parseLong(fecha); return n < 10_000_000_000L ? n * 1000L : n; }
        catch (NumberFormatException ignored) {}

        // Formatos ISO — los que terminan en Z son UTC
        String[][] formatos = {
                {"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "UTC"},
                {"yyyy-MM-dd'T'HH:mm:ssX",       "UTC"},
                {"yyyy-MM-dd'T'HH:mm:ss",        "America/Bogota"},
                {"yyyy-MM-dd HH:mm:ss",          "America/Bogota"},
        };
        for (String[] par : formatos) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(par[0], Locale.getDefault());
                sdf.setTimeZone(java.util.TimeZone.getTimeZone(par[1]));
                Date d = sdf.parse(fecha);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return 0L;
    }
}