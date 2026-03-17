package com.arlys.moviflexx.model;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.arlys.moviflexx.controller.Mapa;
import com.arlys.moviflexx.controller.Mensajes;
import com.arlys.moviflexx.controller.MisReservasActivity;
import com.arlys.moviflexx.controller.MisRutasActivity;
import com.arlys.moviflexx.controller.MisVehiculosActivity;
import com.arlys.moviflexx.controller.PerfilUsuario;
import com.arlys.moviflexx.controller.HomeConductor;
import com.arlys.moviflexx.controller.HomePasajero;
import com.arlys.moviflexx.controller.PublicarRuta;
import com.arlys.moviflexx.model.voice.VoskModelManager;

import org.vosk.Model;
import org.vosk.Recognizer;

import java.util.Locale;

public class VoiceAssistantManager implements TextToSpeech.OnInitListener {

    private static final String TAG = "VoiceAssistant";

    private static VoiceAssistantManager instance;

    private final Context context;
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private volatile boolean isSpeaking = false;

    private volatile boolean running = false;
    private Thread worker;
    private Model voskModel;
    private Recognizer recognizer;
    private AudioRecord audioRecord;

    private enum Mode { WAKE, COMMAND }
    private volatile Mode mode = Mode.WAKE;
    private long commandModeUntilMs = 0L;
    private long lastWakeMs = 0L;

    private static final long WAKE_COOLDOWN_MS  = 2500L;
    private static final long COMMAND_WINDOW_MS = 9000L;

    private VoiceAssistantManager(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, this);
    }

    public static synchronized VoiceAssistantManager getInstance(Context context) {
        if (instance == null) instance = new VoiceAssistantManager(context);
        return instance;
    }

    public static synchronized void destruirInstancia() {
        if (instance != null) { instance.shutdown(); instance = null; }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(new Locale("es", "CO"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                r = tts.setLanguage(new Locale("es"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                tts.setLanguage(Locale.getDefault());
            tts.setSpeechRate(0.95f);
            tts.setPitch(1.1f);
            isInitialized = true;
            setupUtteranceListener();
            configurarVoz();
            Log.d(TAG, "TTS inicializado.");
            showToast("Movi está lista 🎙️");
        } else {
            Log.e(TAG, "TTS falló: " + status);
        }
    }

    private void configurarVoz() {
        if (tts == null) return;
        try {
            android.speech.tts.Voice best = null;
            for (android.speech.tts.Voice v : tts.getVoices()) {
                String n = v.getName().toLowerCase();
                if (!n.contains("es")) continue;
                if (v.isNetworkConnectionRequired()) { best = v; break; }
                if (n.contains("female") || n.contains("femenina") || n.contains("soft")) best = v;
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception e) { Log.w(TAG, "Voz: " + e.getMessage()); }
    }

    private void setupUtteranceListener() {
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String id) { isSpeaking = true; }
            @Override public void onDone(String id)  { isSpeaking = false; }
            @Override public void onError(String id) { isSpeaking = false; }
        });
    }

    public void hablar(String texto) {
        if (!isInitialized || tts == null || texto == null || texto.trim().isEmpty()) return;
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "nav_" + System.currentTimeMillis());
    }

    public void hablarEnCola(String texto) {
        if (!isInitialized || tts == null || texto == null || texto.trim().isEmpty()) return;
        tts.speak(texto, TextToSpeech.QUEUE_ADD, null, "navq_" + System.currentTimeMillis());
    }

    public void detener()  { if (tts != null && isInitialized) tts.stop(); }
    public boolean isListo() { return isInitialized; }
    public void liberar()  { shutdown(); }

    public void saludarConDatoCurioso(String nombre) {
        String[] datos = {
                "¿Sabías que el primer auto del mundo solo alcanzaba los 16 km/h?",
                "El semáforo existió antes que los autos: ¡se usaba para trenes!",
                "En MoviFlex reducimos la huella de carbono al compartir viajes.",
                "Compartir auto con 3 personas reduce emisiones por pasajero hasta un 66%.",
                "El GPS fue diseñado para uso militar y ahora nos lleva a todos lados.",
                "El cinturón de seguridad se inventó en 1959 y ha salvado millones de vidas.",
                "El primer viaje largo en auto lo hizo Bertha Benz en 1888.",
                "Compartir viaje con MoviFlex ayuda a descongestionar el tráfico de la ciudad."
        };
        int i = (int)(Math.random() * datos.length);
        hablarEnCola("¡Hola, " + nombre + "! Soy Movi, qué gusto verte. " + datos[i]);
    }

    public void escuchar() { start(); }

    public synchronized void start() {
        if (running) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Sin permiso RECORD_AUDIO."); return;
        }
        running = true;
        mode = Mode.WAKE;
        worker = new Thread(this::runLoop, "MoviflexVoiceWorker");
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        try { if (audioRecord != null) audioRecord.stop(); } catch (Exception ignored) {}
        try { if (worker != null) worker.interrupt(); } catch (Exception ignored) {}
        worker = null;
    }

    @SuppressLint("MissingPermission")
    private void runLoop() {
        try {
            VoskModelManager.ensureModel(context);
            if (!running) return;

            if (voskModel == null)
                voskModel = new Model(VoskModelManager.getModelDir(context).getAbsolutePath());
            if (recognizer == null)
                recognizer = new Recognizer(voskModel, 16000.0f);

            int minBuf = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(minBuf, 8192);

            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord no inicializado."); running = false; return;
            }
            audioRecord.startRecording();
            Log.d(TAG, "Escuchando (WAKE)…");

            short[] buf = new short[bufSize / 2];
            boolean wasSpeaking = false;
            long lastSpeakEnd = 0L;

            while (running && !Thread.currentThread().isInterrupted()) {
                int n = audioRecord.read(buf, 0, buf.length);
                if (n <= 0) continue;

                long now = System.currentTimeMillis();
                boolean speaking = isSpeaking || (tts != null && tts.isSpeaking());

                if (speaking) { wasSpeaking = true; lastSpeakEnd = now; continue; }
                if (now - lastSpeakEnd < 800) { wasSpeaking = true; continue; }
                if (wasSpeaking) { wasSpeaking = false; try { recognizer.reset(); } catch (Exception ig) {} }

                boolean done = recognizer.acceptWaveForm(buf, n);
                String raw = done ? recognizer.getResult() : recognizer.getPartialResult();
                String txt = normalizeVoskText(raw);
                if (txt.isEmpty()) continue;
                Log.d(TAG, "Vosk: " + txt);

                if (mode == Mode.WAKE) {
                    if (containsWakeWord(txt) && (now - lastWakeMs) > WAKE_COOLDOWN_MS) {
                        lastWakeMs = now;
                        mode = Mode.COMMAND;
                        commandModeUntilMs = now + COMMAND_WINDOW_MS;
                        recognizer.reset();
                        hablar("Hola, soy Movi. ¿En qué puedo ayudarte?");
                        showToast("Movi te escucha 🎙️");
                        continue;
                    }
                    if (procesarComando(txt)) { lastWakeMs = now; recognizer.reset(); }
                    continue;
                }

                if (now > commandModeUntilMs) { mode = Mode.WAKE; continue; }
                if (procesarComando(txt)) { mode = Mode.WAKE; recognizer.reset(); }
            }
        } catch (Exception e) {
            Log.e(TAG, "runLoop error: " + e.getMessage()); running = false;
        } finally {
            try {
                if (audioRecord != null) {
                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) audioRecord.stop();
                    audioRecord.release();
                }
            } catch (Exception ig) {}
            audioRecord = null;
            try { if (recognizer != null) recognizer.close(); } catch (Exception ig) {}
            recognizer = null;
        }
    }

    private boolean procesarComando(String c) {
        Log.d(TAG, "Comando: " + c);
        String lc = c.toLowerCase(Locale.ROOT);

        if ((lc.trim().equals("movi") || lc.contains("desactivar")
                || lc.contains("adiós") || lc.contains("adios")) && mode == Mode.COMMAND) {
            hablar("Entendido, estaré atenta. Solo di Movi cuando me necesites.");
            return true;
        }
        // Cerrar sesión / cerrar perfil
        if (lc.contains("cerrar sesión") || lc.contains("cerrar sesion")
                || lc.contains("cerrar perfil") || lc.contains("salir de la cuenta")
                || lc.contains("cerrar cuenta") || lc.contains("salir de mi cuenta")
                || lc.contains("logout") || lc.contains("log out")) {
            hablar("Cerrando sesión. ¡Hasta pronto!");
            cerrarSesion();
            return true;
        }
        if (lc.contains("buscar viaje") || lc.contains("buscar viajes") || lc.contains("quiero buscar")) {
            hablar("Abriendo búsqueda de viajes."); intentarAbrir(HomePasajero.class); return true; }
        if (lc.contains("mis reservas") || (lc.contains("reserva") && !lc.contains("reservar"))) {
            hablar("Abriendo tus reservas."); intentarAbrir(MisReservasActivity.class); return true; }
        if (lc.contains("publicar") || lc.contains("nuevo viaje") || lc.contains("crear viaje")) {
            hablar("Abriendo publicación de viajes."); intentarAbrir(PublicarRuta.class); return true; }
        if (lc.contains("mis rutas") || lc.contains("rutas")) {
            hablar("Abriendo tus rutas."); intentarAbrir(MisRutasActivity.class); return true; }
        if (lc.contains("mis vehiculos") || lc.contains("vehículos") || lc.contains("vehiculos")) {
            hablar("Abriendo tus vehículos."); intentarAbrir(MisVehiculosActivity.class); return true; }
        if (lc.contains("perfil") || lc.contains("mi cuenta")) {
            hablar("Abriendo tu perfil."); intentarAbrir(PerfilUsuario.class); return true; }
        if (lc.contains("mensajes") || lc.contains("chat")) {
            hablar("Abriendo mensajes."); intentarAbrir(Mensajes.class); return true; }
        if (lc.contains("mapa") || lc.contains("ir al mapa") || lc.contains("donde estoy")) {
            hablar("Abriendo el mapa."); intentarAbrir(Mapa.class); return true; }
        if (lc.contains("inicio") || lc.contains("pantalla principal") || lc.contains("home")) {
            SessionManager s = new SessionManager(context);
            if (s.isConductor()) { hablar("Volviendo al inicio."); intentarAbrir(HomeConductor.class); }
            else { hablar("Volviendo al inicio."); intentarAbrir(HomePasajero.class); }
            return true;
        }
        if (lc.contains("hola") || lc.contains("buenos días") || lc.contains("buenas tardes")) {
            hablar("¡Hola! Soy Movi. ¿En qué puedo ayudarte?"); return true; }
        if (lc.contains("cómo estás") || lc.contains("como estas")) {
            hablar("¡Excelente! Movi lista para acompañarte. ¿Qué necesitas?"); return true; }
        if (lc.contains("quién eres") || lc.contains("quien eres") || lc.contains("como te llamas")) {
            hablar("Soy Movi, tu asistente inteligente de MoviFlex."); return true; }
        if (lc.contains("qué es moviflex") || lc.contains("que es moviflex")) {
            hablar("MoviFlex conecta conductores y pasajeros que van al mismo destino para compartir gastos y reducir emisiones."); return true; }
        if (lc.contains("cómo funciona") || lc.contains("como funciona")) {
            hablar("Si eres pasajero busca y reserva viajes. Si eres conductor publica tu ruta para que otros se unan."); return true; }
        if (lc.contains("pagos") || lc.contains("cómo pago") || lc.contains("cuánto cuesta")) {
            hablar("Los precios se acuerdan antes del viaje. MoviFlex te ayuda a ahorrar compartiendo gastos."); return true; }
        if (lc.contains("seguridad") || lc.contains("es seguro")) {
            hablar("En MoviFlex todos los conductores pasan verificación y los pasajeros pueden calificar cada viaje."); return true; }
        if (lc.contains("ayuda") || lc.contains("qué puedes hacer") || lc.contains("que puedes hacer")) {
            hablar("Puedo abrirte secciones de la app, darte info sobre MoviFlex o guiarte. Di Movi y lo que necesites."); return true; }
        if (lc.contains("gracias")) {
            hablar("Con gusto. ¿Necesitas algo más?"); return true; }
        if (lc.contains("cancelar") || lc.contains("nada") || lc.contains("salir")) {
            hablar("Entendido."); return true; }

        return false;
    }

    private void cerrarSesion() {
        // 1. Limpiar sesión usando SessionManager.logout() (igual que PerfilUsuario)
        try {
            SessionManager session = new SessionManager(context);
            session.logout();
            Log.d(TAG, "Sesión cerrada correctamente.");
        } catch (Exception e) {
            Log.w(TAG, "cerrarSesion error: " + e.getMessage());
        }
        // 2. Ir a Login.class después de que el TTS termine de hablar
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent i = new Intent(context,
                        com.arlys.moviflexx.controller.Login.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(i);
                Log.d(TAG, "Redirigido a Login.");
            } catch (Exception e) {
                Log.e(TAG, "Error abriendo Login: " + e.getMessage());
            }
        }, 1200);
    }

    private void intentarAbrir(Class<?> cls) {
        try {
            Intent i = new Intent(context, cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(i);
        } catch (Exception e) { Log.e(TAG, "Error abriendo " + cls.getSimpleName() + ": " + e.getMessage()); }
    }

    private void showToast(String msg) {
        new Handler(Looper.getMainLooper()).post(
                () -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }

    private static String normalizeVoskText(String s) {
        if (s == null) return "";
        String low = s.toLowerCase(Locale.ROOT);
        if (low.contains("\"partial\"")) return extractJsonValue(low, "\"partial\"").trim();
        if (low.contains("\"text\""))    return extractJsonValue(low, "\"text\"").trim();
        return low.replace("{","").replace("}","").replace("\"","").trim();
    }

    private static String extractJsonValue(String s, String key) {
        int idx = s.indexOf(key);             if (idx < 0)  return "";
        int col = s.indexOf(':', idx+key.length()); if (col < 0) return "";
        int q1  = s.indexOf('"', col+1);      if (q1 < 0)   return "";
        int q2  = s.indexOf('"', q1+1);       if (q2 < 0)   return "";
        return s.substring(q1+1, q2);
    }

    private static boolean containsWakeWord(String t) {
        if (t == null) return false;
        String n = t.toLowerCase(Locale.ROOT)
                .replace(" ","").replace("ó","o").replace("í","i")
                .replace("é","e").replace("á","a");
        return n.contains("movi") || n.contains("mobi") || n.contains("movy");
    }

    public void shutdown() {
        stop();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        isInitialized = false;
        if (voskModel != null) { voskModel.close(); voskModel = null; }
        Log.d(TAG, "VoiceAssistantManager liberado.");
    }
}