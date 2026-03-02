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

    private static final int COLOR_TICK_GRIS = 0xFF80AFAA;
    private static final int COLOR_TICK_TEAL = 0xFF1AB99F;
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
    private final Handler   progressHandler  = new Handler(Looper.getMainLooper());
    private Runnable        progressRunnable = null;

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
        // Header fecha
        if (h.tvFechaHeader != null) {
            h.tvFechaHeader.setVisibility(mostrarFecha ? View.VISIBLE : View.GONE);
            if (mostrarFecha) h.tvFechaHeader.setText(formatearHeaderFecha(m.getFechaEnvio()));
        }

        // Hora
        h.tvHora.setText(formatearHora(m.getFechaEnvio()));

        // Duración
        h.tvDuracion.setText(extraerDuracion(m.getContenido()));

        // Ticks (solo propio)
        if (h.esPropio && h.tvTicks != null) {
            aplicarTicks(h.tvTicks, h.pbEnviando, m);
        }

        // Ruta local — verificar que el archivo exista
        String ruta = m.getRutaAudioLocal();
        boolean archivoDisponible = ruta != null && !ruta.isEmpty() && new File(ruta).exists();

        // Resetear UI según si este VH es el activo
        if (vhActivo == h && mediaPlayerActivo != null) {
            // Este VH sigue siendo el reproductor activo — mantener estado
            h.btnPlayPause.setImageResource(
                    mediaPlayerActivo.isPlaying()
                            ? android.R.drawable.ic_media_pause
                            : android.R.drawable.ic_media_play);
        } else {
            // VH reciclado — resetear
            h.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            h.seekBar.setProgress(0);
            h.tvDuracion.setText(extraerDuracion(m.getContenido()));
        }

        // Opacidad si no hay archivo
        h.btnPlayPause.setAlpha(archivoDisponible ? 1f : 0.45f);

        // Click en play/pause
        h.btnPlayPause.setOnClickListener(v -> {
            if (!archivoDisponible) return;
            toggleReproduccion(h, m, ruta);
        });

        // SeekBar — adelantar/retroceder
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
        // Detener otro reproductor activo
        if (vhActivo != null && vhActivo != h) {
            pararReproductor(true);
        }

        if (vhActivo == h && mediaPlayerActivo != null) {
            // Pausar o reanudar
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
            // Crear nuevo reproductor
            try {
                mediaPlayerActivo = new MediaPlayer();
                mediaPlayerActivo.setDataSource(ruta);
                mediaPlayerActivo.prepare();
                mediaPlayerActivo.start();
                vhActivo   = h;
                rutaActiva = ruta;

                h.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                // Mostrar duración real
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
                        // Mostrar tiempo transcurrido
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
    //  TICKS
    // ─────────────────────────────────────────────────────────────────────────
    private void aplicarTicks(TextView tvTicks, ProgressBar pb, Mensaje m) {
        if (tvTicks == null) return;
        if (m.isEnviando()) {
            tvTicks.setVisibility(View.GONE);
            if (pb != null) pb.setVisibility(View.VISIBLE);
        } else if (m.isFallido()) {
            if (pb != null) pb.setVisibility(View.GONE);
            tvTicks.setVisibility(View.VISIBLE);
            tvTicks.setText("⚠");
            tvTicks.setTextColor(0xFFE53935);
        } else if (m.isLeido()) {
            if (pb != null) pb.setVisibility(View.GONE);
            tvTicks.setVisibility(View.VISIBLE);
            tvTicks.setText("✓✓");
            tvTicks.setTextColor(COLOR_TICK_TEAL);
        } else {
            if (pb != null) pb.setVisibility(View.GONE);
            tvTicks.setVisibility(View.VISIBLE);
            tvTicks.setText("✓✓");
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

    /** Extrae duración de "🎤 Audio (3s)" → "0:03" */
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
        Calendar hoy   = Calendar.getInstance();
        Calendar fecha = Calendar.getInstance(); fecha.setTimeInMillis(ts);
        if (esMismoDia(hoy, fecha)) return "Hoy";
        hoy.add(Calendar.DAY_OF_YEAR, -1);
        if (esMismoDia(hoy, fecha)) return "Ayer";
        Calendar inicioSemana = Calendar.getInstance();
        inicioSemana.add(Calendar.DAY_OF_YEAR, -6);
        if (fecha.after(inicioSemana))
            return new SimpleDateFormat("EEE, d MMM", new Locale("es", "CO")).format(new Date(ts));
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(ts));
    }

    private boolean esMismoDia(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private String formatearHora(String fechaStr) {
        if (fechaStr == null || fechaStr.isEmpty()) return "";
        long ts = parsearTimestamp(fechaStr);
        if (ts == 0) return "";
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    private long parsearTimestamp(String fecha) {
        if (fecha == null || fecha.isEmpty()) return 0L;
        try {
            long num = Long.parseLong(fecha);
            return num < 10_000_000_000L ? num * 1000L : num;
        } catch (NumberFormatException ignored) {}
        String[] fmts = {
                "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd"
        };
        for (String f : fmts) {
            try {
                Date d = new SimpleDateFormat(f, Locale.getDefault()).parse(fecha);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return 0L;
    }
}