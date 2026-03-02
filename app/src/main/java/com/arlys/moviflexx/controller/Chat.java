package com.arlys.moviflexx.controller;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.VolleyError;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Mensaje;
import com.arlys.moviflexx.model.MensajeAdapter;
import com.arlys.moviflexx.model.SessionManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Chat extends AppCompatActivity {

    private static final String TAG                    = "CHAT";
    private static final long   POLL_INTERVAL          = 2500L;
    private static final long   ONLINE_TIMEOUT_MS      = 5 * 60 * 1000L;
    private static final int    MAX_FALLOS_PARA_OFFLINE = 2;

    private static final String[] SUGERENCIAS_PASAJERO = {
            "👋 ¡Hola! ¿Ya saliste hacia el punto de recogida?",
            "📍 ¿Dónde exactamente me recoges?",
            "⏱️ ¿Cuánto tardas en llegar?",
            "💺 ¿Cuántos pasajeros van en el viaje?",
            "🅿️ ¿Puedes recogerme en otro punto?",
            "💰 ¿El precio incluye el trayecto completo?",
            "🧳 ¿Puedo llevar equipaje?",
            "🔔 Avísame cuando estés cerca, por favor",
            "🛣️ ¿Cuál es la ruta que tomarás?",
            "✅ Perfecto, te espero en el punto acordado"
    };

    // ── UI ────────────────────────────────────────────────────────────────────
    private RecyclerView      rvMensajes;
    private MensajeAdapter    adapter;
    private TextInputEditText etMensaje;
    private ImageButton       btnEnviar;
    private ImageButton       btnAudio;
    private ImageButton       btnMenuOpciones;
    private TextView          tvNombreChat;
    private TextView          tvEstado;
    private View              dotEstado;
    private ImageButton       btnBack;
    private LinearLayout      panelSugerencias;
    private ChipGroup         chipGroupSugerencias;
    private boolean           sugerenciasOcultas = false;

    // ── Panel grabación estilo WhatsApp ───────────────────────────────────────
    private LinearLayout panelGrabacion;
    private TextView     tvTiempoGrabacion;
    private TextView     tvDeslizarCancelar;

    // ── Grabación de audio ────────────────────────────────────────────────────
    private MediaRecorder mediaRecorder;
    private MediaPlayer   mediaPlayer;
    private String        rutaAudioTemp;
    private boolean       grabando        = false;
    private boolean       cancelado       = false;
    private long          inicioGrabacion = 0L;
    private float         xInicioTouch    = 0f;

    private final Handler  grabacionHandler  = new Handler(Looper.getMainLooper());
    private       Runnable grabacionRunnable = null;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final List<Mensaje> listaMensajes = new ArrayList<>();
    private SessionManager session;
    private int    idUsuarioActual;
    private long   idConversacion = -1;
    private String nombreContacto = "Chat";

    // ── Presencia ─────────────────────────────────────────────────────────────
    private long    timestampUltimoMensajeContacto = 0L;
    private boolean estabaOnline       = false;
    private boolean conexionActiva     = false;
    private int     fallosConsecutivos = 0;

    // ── Leídos — solo marcado local, el backend no tiene endpoint para esto ───
    // (chatMarcarLeido y chatMensajeMarcarLeido devuelven 404)
    private final Set<Long> idsLeidos = new HashSet<>();

    // ── Polling ───────────────────────────────────────────────────────────────
    private final Handler  handler       = new Handler(Looper.getMainLooper());
    private       Runnable pollRunnable;
    private       boolean  pollingActivo = false;
    private       long     ultimoIdVisto = -1;

    // IDs negativos reservados para mensajes de audio locales
    private final Set<Long> idsAudioLocal = new HashSet<>();

    private final Runnable presenciaRunnable = new Runnable() {
        @Override public void run() {
            actualizarIndicadorPresencia();
            handler.postDelayed(this, 30_000L);
        }
    };

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        session         = new SessionManager(this);
        idUsuarioActual = session.getIdUsuario();
        idConversacion  = getIntent().getLongExtra("idConversacion", -1);
        String nombre   = getIntent().getStringExtra("nombre");
        if (nombre != null && !nombre.isEmpty()) nombreContacto = nombre;

        bindViews();
        configurarHeader();
        configurarRecycler();
        configurarScrollListener();
        configurarInputBar();
        configurarSugerencias();
        configurarMenuOpciones();

        if (idConversacion == -1) {
            Toast.makeText(this, "Conversación inválida", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setEstadoConectando();
        cargarHistorial();
        arrancarPolling();
        handler.post(presenciaRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!pollingActivo) arrancarPolling();
        rvMensajes.post(this::marcarVisiblesComoLeidos);
    }

    @Override protected void onPause() {
        super.onPause();
        detenerPolling();
        handler.removeCallbacks(presenciaRunnable);
        detenerContadorGrabacion();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        detenerPolling();
        if (mediaRecorder != null) { try { mediaRecorder.release(); } catch (Exception ignored) {} }
        if (mediaPlayer   != null) { try { mediaPlayer.release();   } catch (Exception ignored) {} }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BIND
    // ─────────────────────────────────────────────────────────────────────────
    private void bindViews() {
        rvMensajes           = findViewById(R.id.rvMensajes);
        etMensaje            = findViewById(R.id.etMensaje);
        btnEnviar            = findViewById(R.id.btnEnviar);
        btnAudio             = findViewById(R.id.btnAudio);
        tvNombreChat         = findViewById(R.id.tvNombreChat);
        tvEstado             = findViewById(R.id.tvEstado);
        dotEstado            = findViewById(R.id.dotEstado);
        btnBack              = findViewById(R.id.btnBack);
        btnMenuOpciones      = findViewById(R.id.btnMenuOpciones);
        panelSugerencias     = findViewById(R.id.panel_sugerencias);
        chipGroupSugerencias = findViewById(R.id.chip_group_sugerencias);
        panelGrabacion       = findViewById(R.id.panelGrabacion);
        tvTiempoGrabacion    = findViewById(R.id.tvTiempoGrabacion);
        tvDeslizarCancelar   = findViewById(R.id.tvDeslizarCancelar);
    }

    private void configurarHeader() {
        if (tvNombreChat != null) tvNombreChat.setText(nombreContacto);
        setEstadoConectando();

        TextView tvAvatar = findViewById(R.id.tvAvatarHeader);
        if (tvAvatar != null && !nombreContacto.isEmpty())
            tvAvatar.setText(String.valueOf(nombreContacto.charAt(0)).toUpperCase());

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                animarBotonBack();
                new Handler().postDelayed(this::finish, 150);
            });
            btnBack.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.88f).scaleY(0.88f).alpha(0.8f).setDuration(110).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(110).start();
                        break;
                }
                return false;
            });
        }
    }

    private void configurarRecycler() {
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        rvMensajes.setLayoutManager(llm);
        rvMensajes.setItemAnimator(null);

        adapter = new MensajeAdapter(listaMensajes, idUsuarioActual);
        adapter.setOnMensajeVistoListener(mensaje -> { /* scroll listener se encarga */ });
        rvMensajes.setAdapter(adapter);
    }

    private void configurarScrollListener() {
        rvMensajes.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                marcarVisiblesComoLeidos();
            }
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) marcarVisiblesComoLeidos();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INPUT BAR
    // ─────────────────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private void configurarInputBar() {
        btnEnviar.setEnabled(false);
        btnEnviar.setAlpha(0.35f);
        btnEnviar.setVisibility(View.GONE);
        if (btnAudio != null) btnAudio.setVisibility(View.VISIBLE);

        etMensaje.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean hayTexto = s.toString().trim().length() > 0;
                if (hayTexto) {
                    if (btnAudio != null && btnAudio.getVisibility() == View.VISIBLE) {
                        btnAudio.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(120)
                                .withEndAction(() -> btnAudio.setVisibility(View.GONE)).start();
                        btnEnviar.setVisibility(View.VISIBLE);
                        btnEnviar.setScaleX(0f); btnEnviar.setScaleY(0f); btnEnviar.setAlpha(0f);
                        btnEnviar.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(150)
                                .withEndAction(() -> btnEnviar.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
                    }
                    btnEnviar.setEnabled(true);
                    btnEnviar.setAlpha(1f);
                } else {
                    if (btnEnviar.getVisibility() == View.VISIBLE) {
                        btnEnviar.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(120)
                                .withEndAction(() -> {
                                    btnEnviar.setVisibility(View.GONE);
                                    btnEnviar.setEnabled(false);
                                    btnEnviar.setAlpha(0.35f);
                                }).start();
                        if (btnAudio != null) {
                            btnAudio.setVisibility(View.VISIBLE);
                            btnAudio.setScaleX(0f); btnAudio.setScaleY(0f); btnAudio.setAlpha(0f);
                            btnAudio.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start();
                        }
                    }
                }
            }
        });

        btnEnviar.setOnClickListener(v -> enviarMensaje());

        if (btnAudio != null) {
            btnAudio.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        xInicioTouch = event.getRawX();
                        iniciarGrabacion();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (grabando) {
                            float dx = xInicioTouch - event.getRawX();
                            if (dx > dpToPx(120)) {
                                cancelarGrabacion();
                            } else {
                                if (tvDeslizarCancelar != null)
                                    tvDeslizarCancelar.setAlpha(Math.max(0f, 1f - dx / dpToPx(120)));
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (grabando) detenerYEnviarAudio();
                        return true;
                }
                return false;
            });

            btnAudio.setOnClickListener(v ->
                    Toast.makeText(this, "Mantén presionado para grabar 🎤", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GRABACIÓN
    // ─────────────────────────────────────────────────────────────────────────
    private void iniciarGrabacion() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
                return;
            }
        }
        try {
            rutaAudioTemp = getCacheDir().getAbsolutePath() + "/audio_" + System.currentTimeMillis() + ".m4a";
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setOutputFile(rutaAudioTemp);
            mediaRecorder.prepare();
            mediaRecorder.start();

            grabando        = true;
            cancelado       = false;
            inicioGrabacion = System.currentTimeMillis();

            mostrarPanelGrabacion(true);
            if (btnAudio != null) { btnAudio.setColorFilter(Color.RED); pulsarBotonAudio(true); }
            iniciarContadorGrabacion();

        } catch (Exception e) {
            Log.e(TAG, "❌ iniciarGrabacion", e);
            Toast.makeText(this, "No se pudo iniciar la grabación", Toast.LENGTH_SHORT).show();
            grabando = false;
        }
    }

    private void detenerYEnviarAudio() {
        if (!grabando || mediaRecorder == null) return;
        grabando = false;
        try { mediaRecorder.stop(); mediaRecorder.release(); mediaRecorder = null; }
        catch (Exception e) { Log.e(TAG, "❌ stop recorder", e); }

        detenerContadorGrabacion();
        mostrarPanelGrabacion(false);
        if (btnAudio != null) { btnAudio.clearColorFilter(); pulsarBotonAudio(false); }

        if (cancelado) { borrarAudioTemp(); cancelado = false; return; }

        long duracionMs = System.currentTimeMillis() - inicioGrabacion;
        if (duracionMs < 1000) { borrarAudioTemp(); Toast.makeText(this, "Audio muy corto", Toast.LENGTH_SHORT).show(); return; }

        long seg = duracionMs / 1000;
        enviarMensajeAudio(rutaAudioTemp, seg < 60 ? seg + "s" : (seg / 60) + "m " + (seg % 60) + "s");
    }

    private void cancelarGrabacion() {
        cancelado = true;
        detenerYEnviarAudio();
        Toast.makeText(this, "Grabación cancelada", Toast.LENGTH_SHORT).show();
    }

    private void borrarAudioTemp() {
        if (rutaAudioTemp != null) { new File(rutaAudioTemp).delete(); rutaAudioTemp = null; }
    }

    private void mostrarPanelGrabacion(boolean mostrar) {
        if (panelGrabacion == null) return;
        if (mostrar) {
            panelGrabacion.setVisibility(View.VISIBLE);
            panelGrabacion.setAlpha(0f);
            panelGrabacion.animate().alpha(1f).setDuration(180).start();
            if (etMensaje != null) etMensaje.setVisibility(View.INVISIBLE);
        } else {
            panelGrabacion.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                panelGrabacion.setVisibility(View.GONE);
                if (etMensaje != null) etMensaje.setVisibility(View.VISIBLE);
                if (tvTiempoGrabacion  != null) tvTiempoGrabacion.setText("0:00");
                if (tvDeslizarCancelar != null) tvDeslizarCancelar.setAlpha(1f);
            }).start();
        }
    }

    private void iniciarContadorGrabacion() {
        detenerContadorGrabacion();
        grabacionRunnable = new Runnable() {
            @Override public void run() {
                if (!grabando) return;
                long elapsed = (System.currentTimeMillis() - inicioGrabacion) / 1000;
                if (tvTiempoGrabacion != null)
                    tvTiempoGrabacion.setText((elapsed / 60) + ":" + String.format(Locale.getDefault(), "%02d", elapsed % 60));
                grabacionHandler.postDelayed(this, 1000);
            }
        };
        grabacionHandler.post(grabacionRunnable);
    }

    private void detenerContadorGrabacion() {
        if (grabacionRunnable != null) { grabacionHandler.removeCallbacks(grabacionRunnable); grabacionRunnable = null; }
    }

    private void pulsarBotonAudio(boolean activar) {
        if (btnAudio == null) return;
        if (activar) {
            android.animation.ObjectAnimator px = android.animation.ObjectAnimator.ofFloat(btnAudio, "scaleX", 1f, 1.3f, 1f);
            px.setDuration(600); px.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            px.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()); px.start();
            android.animation.ObjectAnimator py = android.animation.ObjectAnimator.ofFloat(btnAudio, "scaleY", 1f, 1.3f, 1f);
            py.setDuration(600); py.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            py.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()); py.start();
        } else {
            btnAudio.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Permiso concedido. Mantén el botón para grabar.", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(this, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ENVIAR AUDIO
    // ─────────────────────────────────────────────────────────────────────────
    private void enviarMensajeAudio(String ruta, String duracion) {
        long idLocal = -System.currentTimeMillis();
        idsAudioLocal.add(idLocal);

        Mensaje opt = new Mensaje();
        opt.setId(idLocal);
        opt.setContenido("🎤 Audio (" + duracion + ")");
        opt.setIdEmisor(idUsuarioActual);
        opt.setEsPropio(true);
        opt.setEnviando(true);
        opt.setLeido(false);
        opt.setTipoAudio(true);
        opt.setRutaAudioLocal(ruta);
        opt.setFechaEnvio(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date()));

        listaMensajes.add(opt);
        adapter.notifyItemInserted(listaMensajes.size() - 1);
        scrollAbajo();

        new Handler().postDelayed(() -> runOnUiThread(() -> {
            opt.setEnviando(false);
            int idx = listaMensajes.indexOf(opt);
            if (idx >= 0) adapter.notifyItemChanged(idx);
        }), 500);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SUGERENCIAS
    // ─────────────────────────────────────────────────────────────────────────
    private void configurarSugerencias() {
        if (session.isConductor() || panelSugerencias == null || chipGroupSugerencias == null) {
            if (panelSugerencias != null) panelSugerencias.setVisibility(View.GONE);
            return;
        }
        chipGroupSugerencias.removeAllViews();
        for (String texto : SUGERENCIAS_PASAJERO) {
            Chip chip = new Chip(this);
            chip.setText(texto);
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setTextSize(13f);
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#D8F5EF")));
            chip.setTextColor(Color.parseColor("#0D6B5C"));
            chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor("#A8EDE4")));
            chip.setChipStrokeWidth(1.5f);
            chip.setRippleColor(ColorStateList.valueOf(Color.parseColor("#B2EDE7")));
            chip.setOnClickListener(v -> {
                if (etMensaje != null) {
                    etMensaje.setText(texto);
                    etMensaje.setSelection(texto.length());
                    etMensaje.requestFocus();
                }
            });
            chipGroupSugerencias.addView(chip);
        }
        View btnCerrar = findViewById(R.id.btn_cerrar_sugerencias);
        if (btnCerrar != null) btnCerrar.setOnClickListener(v -> ocultarSugerencias(true));

        panelSugerencias.setVisibility(View.VISIBLE);
        panelSugerencias.setTranslationY(80f);
        panelSugerencias.setAlpha(0f);
        panelSugerencias.animate().translationY(0f).alpha(1f).setDuration(300)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();
    }

    private void ocultarSugerencias(boolean animado) {
        if (sugerenciasOcultas || panelSugerencias == null) return;
        sugerenciasOcultas = true;
        if (animado) {
            panelSugerencias.animate().translationY(panelSugerencias.getHeight()).alpha(0f).setDuration(240)
                    .withEndAction(() -> {
                        panelSugerencias.setVisibility(View.GONE);
                        panelSugerencias.setTranslationY(0f);
                        panelSugerencias.setAlpha(1f);
                    }).start();
        } else {
            panelSugerencias.setVisibility(View.GONE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HISTORIAL
    // ─────────────────────────────────────────────────────────────────────────
    private void cargarHistorial() {
        String url = Constantes.chatMensajesPorConversacion(idConversacion);
        ConexionApi.getInstance(this).getArray(url, this::onHistorialOk, this::onHistorialError);
    }

    private void onHistorialOk(JSONArray arr) {
        fallosConsecutivos = 0; conexionActiva = true;
        runOnUiThread(() -> {
            procesarArray(arr, true);
            actualizarIndicadorPresencia();
            rvMensajes.post(this::marcarVisiblesComoLeidos);
        });
    }

    private void onHistorialError(VolleyError error) {
        fallosConsecutivos++;
        String url = Constantes.chatMensajesPorConversacion(idConversacion);
        ConexionApi.getInstance(this).getObject(url,
                resp -> {
                    fallosConsecutivos = 0; conexionActiva = true;
                    runOnUiThread(() -> {
                        procesarArray(extraerArray(resp), true);
                        actualizarIndicadorPresencia();
                        rvMensajes.post(this::marcarVisiblesComoLeidos);
                    });
                },
                err2 -> runOnUiThread(() -> { fallosConsecutivos++; conexionActiva = false; setEstadoSinConexion(); })
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PRESENCIA
    // ─────────────────────────────────────────────────────────────────────────
    private void actualizarIndicadorPresencia() {
        if (tvEstado == null || dotEstado == null) return;
        if (!conexionActiva || fallosConsecutivos >= MAX_FALLOS_PARA_OFFLINE) { setEstadoSinConexion(); return; }
        if (timestampUltimoMensajeContacto == 0L) { setEstadoDesconocido(); return; }

        long diferencia = System.currentTimeMillis() - timestampUltimoMensajeContacto;
        if (diferencia < ONLINE_TIMEOUT_MS) {
            if (!estabaOnline) { estabaOnline = true; animarCambioEstado(); }
            tvEstado.setText("En línea");
            dotEstado.setBackgroundResource(R.drawable.circle_online);
            dotEstado.setAlpha(1f); dotEstado.setVisibility(View.VISIBLE);
        } else {
            if (estabaOnline) { estabaOnline = false; animarCambioEstado(); }
            tvEstado.setText("Última vez " + formatearTiempoRelativo(diferencia));
            dotEstado.setBackgroundResource(R.drawable.circle_offline);
            dotEstado.setAlpha(1f); dotEstado.setVisibility(View.VISIBLE);
        }
    }

    private String formatearTiempoRelativo(long ms) {
        long min = ms / 60_000L;
        if (min < 2)  return "hace un momento";
        if (min < 60) return "hace " + min + " min";
        long h = min / 60;
        if (h < 24)   return "hace " + h + " h";
        long d = h / 24;
        if (d == 1)   return "ayer";
        if (d < 7)    return "hace " + d + " días";
        return "hace mucho tiempo";
    }

    private void setEstadoConectando() {
        if (tvEstado == null || dotEstado == null) return;
        tvEstado.setText("Conectando..."); dotEstado.setBackgroundResource(R.drawable.circle_offline);
        dotEstado.setAlpha(0.5f); dotEstado.setVisibility(View.VISIBLE);
    }

    private void setEstadoSinConexion() {
        if (tvEstado == null || dotEstado == null) return;
        tvEstado.setText("Sin conexión"); dotEstado.setBackgroundResource(R.drawable.circle_offline);
        dotEstado.setAlpha(1f); dotEstado.setVisibility(View.VISIBLE); estabaOnline = false;
    }

    private void setEstadoDesconocido() {
        if (tvEstado == null || dotEstado == null) return;
        tvEstado.setText(""); dotEstado.setVisibility(View.GONE);
    }

    private void animarCambioEstado() {
        if (tvEstado == null) return;
        tvEstado.animate().alpha(0f).setDuration(140)
                .withEndAction(() -> tvEstado.animate().alpha(1f).setDuration(180).start()).start();
        if (dotEstado != null) {
            dotEstado.setVisibility(View.VISIBLE);
            dotEstado.animate().scaleX(1.5f).scaleY(1.5f).setDuration(140)
                    .withEndAction(() -> dotEstado.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PROCESAR MENSAJES
    // ─────────────────────────────────────────────────────────────────────────
    @SuppressLint("NotifyDataSetChanged")
    private void procesarArray(JSONArray arr, boolean limpiar) {
        if (arr == null) return;
        try {
            if (limpiar) {
                List<Mensaje> audioLocales = new ArrayList<>();
                for (Mensaje m : listaMensajes) if (idsAudioLocal.contains(m.getId())) audioLocales.add(m);
                listaMensajes.clear();
                listaMensajes.addAll(audioLocales);
            }

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                Mensaje m = Mensaje.fromJson(obj, idUsuarioActual);

                if (limpiar) {
                    listaMensajes.add(m);
                } else {
                    if (m.getId() > 0 && m.getId() <= ultimoIdVisto) continue;
                    int idx = m.isTipoAudio() ? -1 : buscarOptimista(m.getContenido());
                    if (idx >= 0) { listaMensajes.set(idx, m); adapter.notifyItemChanged(idx); }
                    else { listaMensajes.add(m); adapter.notifyItemInserted(listaMensajes.size() - 1); }
                }

                if (m.getId() > ultimoIdVisto) ultimoIdVisto = m.getId();
                if (!m.isEsPropio()) {
                    long ts = parsearTimestamp(m.getFechaEnvio());
                    if (ts > timestampUltimoMensajeContacto) timestampUltimoMensajeContacto = ts;
                }
            }

            if (limpiar) adapter.notifyDataSetChanged();
            scrollAbajo();
            if (limpiar && hayMensajesPropios()) ocultarSugerencias(false);

        } catch (Exception e) { Log.e(TAG, "❌ procesarArray", e); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LEÍDOS — solo marcado local en UI
    //  El backend NO tiene endpoint para marcar mensajes como leídos.
    //  Las llamadas a chatMarcarLeido / chatMensajeMarcarLeido devuelven 404.
    // ─────────────────────────────────────────────────────────────────────────
    @SuppressLint("NotifyDataSetChanged")
    private void marcarVisiblesComoLeidos() {
        LinearLayoutManager llm = (LinearLayoutManager) rvMensajes.getLayoutManager();
        if (llm == null || listaMensajes.isEmpty()) return;

        int primerVisible = llm.findFirstCompletelyVisibleItemPosition();
        int ultimoVisible = llm.findLastCompletelyVisibleItemPosition();
        if (primerVisible < 0) primerVisible = llm.findFirstVisibleItemPosition();
        if (ultimoVisible < 0) ultimoVisible  = llm.findLastVisibleItemPosition();
        if (primerVisible < 0 || ultimoVisible < 0) return;

        boolean huboCambios = false;
        for (int i = primerVisible; i <= ultimoVisible && i < listaMensajes.size(); i++) {
            Mensaje m = listaMensajes.get(i);
            if (!m.isEsPropio() && m.getId() > 0 && !m.isLeido()) {
                m.setLeido(true);
                idsLeidos.add(m.getId());
                huboCambios = true;
            }
        }
        if (huboCambios) adapter.notifyDataSetChanged();
        // No llamamos al backend — no existe el endpoint
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POLLING
    // ─────────────────────────────────────────────────────────────────────────
    private void arrancarPolling() {
        if (pollingActivo) return;
        pollingActivo = true;
        pollRunnable = new Runnable() {
            @Override public void run() {
                if (!pollingActivo) return;
                pedirNuevos();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        handler.postDelayed(pollRunnable, POLL_INTERVAL);
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
    }

    private void pedirNuevos() {
        String url = Constantes.chatMensajesPorConversacion(idConversacion);
        ConexionApi.getInstance(this).getArray(url,
                arr -> {
                    fallosConsecutivos = 0; conexionActiva = true;
                    runOnUiThread(() -> {
                        procesarArray(arr, false);
                        actualizarIndicadorPresencia();
                        rvMensajes.post(this::marcarVisiblesComoLeidos);
                    });
                },
                err -> ConexionApi.getInstance(this).getObject(url,
                        resp -> {
                            fallosConsecutivos = 0; conexionActiva = true;
                            runOnUiThread(() -> {
                                procesarArray(extraerArray(resp), false);
                                actualizarIndicadorPresencia();
                                rvMensajes.post(this::marcarVisiblesComoLeidos);
                            });
                        },
                        err2 -> runOnUiThread(() -> {
                            fallosConsecutivos++;
                            conexionActiva = fallosConsecutivos < MAX_FALLOS_PARA_OFFLINE;
                            actualizarIndicadorPresencia();
                        })
                )
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ENVIAR MENSAJE DE TEXTO
    // ─────────────────────────────────────────────────────────────────────────
    private void enviarMensaje() {
        if (etMensaje.getText() == null) return;
        String texto = etMensaje.getText().toString().trim();
        if (texto.isEmpty()) return;

        ocultarSugerencias(true);
        etMensaje.setText("");
        btnEnviar.setEnabled(false);
        btnEnviar.setAlpha(0.35f);

        Mensaje opt = new Mensaje();
        opt.setId(-System.currentTimeMillis());
        opt.setContenido(texto);
        opt.setIdEmisor(idUsuarioActual);
        opt.setEsPropio(true);
        opt.setEnviando(true);
        opt.setLeido(false);
        opt.setFechaEnvio(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date()));

        listaMensajes.add(opt);
        adapter.notifyItemInserted(listaMensajes.size() - 1);
        scrollAbajo();

        JSONObject body = new JSONObject();
        try {
            body.put("idConversacion", idConversacion);
            body.put("idRemitente",    idUsuarioActual);
            body.put("mensaje",        texto);
            body.put("tipo",           "TEXTO");
            body.put("conversacionId", idConversacion);
            body.put("emisorId",       idUsuarioActual);
            body.put("contenido",      texto);
        } catch (JSONException e) { Log.e(TAG, "❌ JSON body", e); marcarFallido(opt); return; }

        ConexionApi.getInstance(this).post(Constantes.CHAT_MENSAJES, body,
                response -> runOnUiThread(() -> confirmarEnvio(opt, response)),
                error    -> runOnUiThread(() -> marcarFallido(opt))
        );
    }

    private void confirmarEnvio(Mensaje opt, JSONObject response) {
        try {
            Mensaje real = Mensaje.fromJson(response, idUsuarioActual);
            real.setLeido(false);
            int idx = listaMensajes.indexOf(opt);
            if (idx >= 0) { listaMensajes.set(idx, real); adapter.notifyItemChanged(idx); }
            if (real.getId() > ultimoIdVisto) ultimoIdVisto = real.getId();
        } catch (Exception e) { marcarFallido(opt); }
    }

    private void marcarFallido(Mensaje m) {
        m.setEnviando(false); m.setFallido(true);
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "No se pudo enviar el mensaje", Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private JSONArray extraerArray(JSONObject response) {
        if (response == null) return null;
        if (response.has("mensajes")) return response.optJSONArray("mensajes");
        if (response.has("content"))  return response.optJSONArray("content");
        if (response.has("data"))     return response.optJSONArray("data");
        try { if (response.toString().startsWith("[")) return new JSONArray(response.toString()); }
        catch (Exception ignored) {}
        return null;
    }

    private boolean hayMensajesPropios() {
        for (Mensaje m : listaMensajes) if (m.isEsPropio() || m.getIdEmisor() == idUsuarioActual) return true;
        return false;
    }

    private int buscarOptimista(String contenido) {
        for (int i = listaMensajes.size() - 1; i >= 0; i--) {
            Mensaje m = listaMensajes.get(i);
            if (m.isEnviando() && !m.isTipoAudio() && contenido != null && contenido.equals(m.getContenido())) return i;
        }
        return -1;
    }

    private long parsearTimestamp(String fecha) {
        if (fecha == null || fecha.isEmpty()) return 0L;
        try { long n = Long.parseLong(fecha); return n < 10_000_000_000L ? n * 1000L : n; }
        catch (NumberFormatException ignored) {}
        for (String f : new String[]{"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"}) {
            try { Date d = new SimpleDateFormat(f, Locale.getDefault()).parse(fecha); if (d != null) return d.getTime(); }
            catch (Exception ignored) {}
        }
        return 0L;
    }

    private void scrollAbajo() {
        if (!listaMensajes.isEmpty())
            rvMensajes.post(() -> rvMensajes.scrollToPosition(listaMensajes.size() - 1));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MENÚ TRES PUNTOS
    // ─────────────────────────────────────────────────────────────────────────
    private void configurarMenuOpciones() {
        if (btnMenuOpciones == null) return;
        btnMenuOpciones.setOnClickListener(this::mostrarMenuOpciones);
    }

    private void mostrarMenuOpciones(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Buscar");
        popup.getMenu().add(0, 2, 1, "Archivos, enlaces y docs.");
        popup.getMenu().add(0, 3, 2, "Mensajes temporales");
        popup.getMenu().add(0, 4, 3, "Tema del chat");
        popup.getMenu().add(0, 5, 4, "Más ▶");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: accionBuscar(); return true;
                case 2: accionArchivosYDocs(); return true;
                case 3: accionMensajesTemporales(); return true;
                case 4: accionTemaChat(); return true;
                case 5: mostrarSubMenuMas(anchor); return true;
            }
            return false;
        });
        popup.show();
    }

    private void mostrarSubMenuMas(View anchor) {
        PopupMenu sub = new PopupMenu(this, anchor);
        sub.getMenu().add(0, 10, 0, "Vaciar chat");
        sub.getMenu().add(0, 11, 1, "Exportar chat");
        sub.getMenu().add(0, 12, 2, "Crear acceso directo");
        sub.getMenu().add(0, 13, 3, "Añadir a lista");
        sub.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 10: confirmarVaciarChat(); return true;
                case 11: accionExportarChat();  return true;
                case 12: accionAccesoDirecto(); return true;
                case 13: accionAnadirALista();  return true;
            }
            return false;
        });
        sub.show();
    }

    private void accionBuscar()             { Toast.makeText(this, "Buscar (próximamente)", Toast.LENGTH_SHORT).show(); }
    private void accionArchivosYDocs()      { Toast.makeText(this, "Archivos, enlaces y docs.", Toast.LENGTH_SHORT).show(); }
    private void accionTemaChat()           { Toast.makeText(this, "Tema del chat (próximamente)", Toast.LENGTH_SHORT).show(); }
    private void accionAccesoDirecto()      { Toast.makeText(this, "Acceso directo a " + nombreContacto + " creado", Toast.LENGTH_SHORT).show(); }
    private void accionAnadirALista()       { Toast.makeText(this, "Añadido a la lista de favoritos", Toast.LENGTH_SHORT).show(); }

    private void accionMensajesTemporales() {
        new AlertDialog.Builder(this)
                .setTitle("Mensajes temporales")
                .setMessage("Los mensajes temporales se eliminan automáticamente.")
                .setPositiveButton("Activar 24h", (d, w) -> Toast.makeText(this, "Mensajes temporales: 24h", Toast.LENGTH_SHORT).show())
                .setNegativeButton("Desactivar", null)
                .setNeutralButton("Cancelar", null).show();
    }

    private void confirmarVaciarChat() {
        new AlertDialog.Builder(this)
                .setTitle("Vaciar chat")
                .setMessage("¿Eliminar todos los mensajes? Esta acción no se puede deshacer.")
                .setPositiveButton("Vaciar", (d, w) -> {
                    listaMensajes.clear(); idsAudioLocal.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Chat vaciado", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void accionExportarChat() {
        if (listaMensajes.isEmpty()) { Toast.makeText(this, "No hay mensajes", Toast.LENGTH_SHORT).show(); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("Chat con ").append(nombreContacto).append("\n─────────────────────\n");
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        for (Mensaje m : listaMensajes) {
            String quien = m.isEsPropio() ? "Yo" : nombreContacto;
            String hora = "";
            try { long ts = Long.parseLong(m.getFechaEnvio()); if (ts < 10_000_000_000L) ts *= 1000L; hora = " [" + sdf.format(new Date(ts)) + "]"; }
            catch (Exception ignored) {}
            sb.append(quien).append(hora).append(": ").append(m.getContenido()).append("\n");
        }
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(android.content.Intent.EXTRA_TEXT, sb.toString());
        startActivity(android.content.Intent.createChooser(i, "Exportar chat"));
    }

    private void animarBotonBack() {
        if (btnBack == null) return;
        btnBack.animate().rotation(-12f).setDuration(90)
                .withEndAction(() -> btnBack.animate().rotation(0f).setDuration(90).start()).start();
    }
}