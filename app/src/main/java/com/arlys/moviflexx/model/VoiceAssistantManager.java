package com.arlys.moviflexx.model;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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

import com.arlys.moviflexx.MyApplication;
import com.arlys.moviflexx.controller.BuscarRuta;
import com.arlys.moviflexx.controller.Mapa;
import com.arlys.moviflexx.controller.Mensajes;
import com.arlys.moviflexx.controller.MisReservasActivity;
import com.arlys.moviflexx.controller.MisRutasActivity;
import com.arlys.moviflexx.controller.MisVehiculosActivity;
import com.arlys.moviflexx.controller.PerfilUsuario;
import com.arlys.moviflexx.controller.HomeConductor;
import com.arlys.moviflexx.controller.HomePasajero;
import com.arlys.moviflexx.controller.PublicarRuta;
import com.arlys.moviflexx.model.voice.ScreenDescriptor;
import com.arlys.moviflexx.model.voice.VoiceFlowManager;
import com.arlys.moviflexx.model.voice.VoskModelManager;

import org.vosk.Model;
import org.vosk.Recognizer;

import java.util.Locale;

/**
 * Cerebro del asistente de voz Movi.
 * Singleton que maneja: reconocimiento de voz (Vosk), TTS, wake word,
 * lectura de pantalla, ayuda contextual, flujos guiados y navegación por voz.
 */
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

    /** 
     * Flag crítico para Espresso E2E: 
     * Si es true, NUNCA activa el micrófono ni procesa comandos 
     * para evitar que el ruido ambiental reviente la UI del test. 
     */
    public static boolean isTestMode = false;

    /** Estado de activación del asistente. */
    private volatile boolean assistantEnabled = true;

    // ── Modos del reconocedor ───────────────────────────────────────────
    private enum Mode { WAKE, COMMAND }
    private volatile Mode mode = Mode.WAKE;
    private long commandModeUntilMs = 0L;
    private long lastWakeMs = 0L;

    private static final long WAKE_COOLDOWN_MS  = 2500L;
    private static final long COMMAND_WINDOW_MS = 12000L; // Ampliado de 9s a 12s

    // ── Último mensaje (para "Repite") ──────────────────────────────────
    private String ultimoMensaje = "";

    // ── Flujo guiado de reserva ─────────────────────────────────────────
    private VoiceFlowManager voiceFlow;

    // =====================================================================
    //  CONSTRUCTOR & SINGLETON
    // =====================================================================

    private VoiceAssistantManager(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, this);
        voiceFlow = new VoiceFlowManager(this.context, this);
    }

    public static synchronized VoiceAssistantManager getInstance(Context context) {
        if (instance == null) instance = new VoiceAssistantManager(context);
        return instance;
    }

    public static synchronized void destruirInstancia() {
        if (instance != null) { instance.shutdown(); instance = null; }
    }

    // =====================================================================
    //  TTS INIT
    // =====================================================================

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(new Locale("es", "CO"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                r = tts.setLanguage(new Locale("es"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                tts.setLanguage(Locale.getDefault());
            isInitialized = true;
            setupUtteranceListener();
            configurarVoz();
            Log.d(TAG, "TTS inicializado (Modo JARVIS).");
            showToast("Asistente listo");
        } else {
            Log.e(TAG, "TTS falló: " + status);
        }
    }

    private void configurarVoz() {
        if (tts == null) return;
        try {
            android.speech.tts.Voice bestMale = null;
            for (android.speech.tts.Voice v : tts.getVoices()) {
                String n = v.getName().toLowerCase();
                if (!n.contains("es")) continue;
                // Priorizar voces masculinas para simular a JARVIS
                if (n.contains("male") && !n.contains("female") || n.contains("masculina")) {
                    bestMale = v;
                    if (!v.isNetworkConnectionRequired()) break; // Voz local masculina es ideal
                }
            }
            if (bestMale != null) {
                tts.setVoice(bestMale);
                Log.d(TAG, "Voz seleccionada (JARVIS): " + bestMale.getName());
            } else {
                Log.d(TAG, "No se encontró voz masculina explícita.");
            }
            
            // Tono ligeramente grave, no en exceso (0.85f) para evitar que suene robótico/"feo"
            tts.setPitch(0.85f); 
            tts.setSpeechRate(0.95f);
            
        } catch (Exception e) { Log.w(TAG, "Error configurando voz: " + e.getMessage()); }
    }

    private void setupUtteranceListener() {
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String id) { isSpeaking = true; }
            @Override public void onDone(String id)  { isSpeaking = false; }
            @Override public void onError(String id) { isSpeaking = false; }
        });
    }

    // =====================================================================
    //  HABLAR (TTS) — guarda último mensaje para "Repite"
    // =====================================================================

    public void hablar(String texto) {
        if (!isInitialized || tts == null || texto == null || texto.trim().isEmpty()) return;
        ultimoMensaje = texto;
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "nav_" + System.currentTimeMillis());
    }

    public void hablarEnCola(String texto) {
        if (!isInitialized || tts == null || texto == null || texto.trim().isEmpty()) return;
        ultimoMensaje = texto;
        tts.speak(texto, TextToSpeech.QUEUE_ADD, null, "navq_" + System.currentTimeMillis());
    }

    public void detener()  { if (tts != null && isInitialized) tts.stop(); }
    public boolean isListo() { return isInitialized; }
    public void liberar()  { shutdown(); }

    public VoiceFlowManager getFlowManager() {
        return voiceFlow;
    }

    public Activity getCurrentActivity() {
        return MyApplication.getCurrentActivity();
    }

    // =====================================================================
    //  SALUDO CON DATO CURIOSO
    // =====================================================================

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
        
        String saludoBase = "Saludos, " + nombre + ". Sistemas en línea. " + datos[i];
        String clima = obtenerClimaSimulado();
        
        hablarEnCola(saludoBase + " Además, " + clima + " ¿A dónde nos dirigimos hoy?");
    }

    private String obtenerClimaSimulado() {
        String[] climas = {
            "el clima actual es despejado con una agradable temperatura de 22 grados centígrados.",
            "hoy tenemos un día soleado, perfecto para iniciar la ruta.",
            "actualmente el cielo está parcialmente nublado con 19 grados.",
            "el clima se percibe un poco fresco hoy con 16 grados. Sugiero llevar abrigo."
        };
        return climas[(int)(Math.random() * climas.length)];
    }

    // =====================================================================
    //  ESCUCHA (VOSK STT)
    // =====================================================================

    public void escuchar() { start(); }

    public synchronized void start() {
        // En tests E2E, bloqueamos la activación del micrófono
        if (isTestMode) {
            Log.d(TAG, "Test E2E en curso: Asistente de voz bloqueado.");
            return;
        }
        
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

                // Lógica de Activación Especial cuando está DESACTIVADO
                if (!assistantEnabled) {
                    if (txt.toLowerCase(Locale.ROOT).contains("activar")) {
                        assistantEnabled = true;
                        lastWakeMs = now;
                        mode = Mode.COMMAND;
                        commandModeUntilMs = now + COMMAND_WINDOW_MS;
                        recognizer.reset();
                        hablar("Sistemas reactivados. ¿En qué puedo asistirle?");
                        showToast("Asistente activado 🎙️");
                    }
                    continue; 
                }

                if (mode == Mode.WAKE) {
                    if (containsWakeWord(txt) && (now - lastWakeMs) > WAKE_COOLDOWN_MS) {
                        lastWakeMs = now;
                        mode = Mode.COMMAND;
                        commandModeUntilMs = now + COMMAND_WINDOW_MS;
                        recognizer.reset();
                        hablar("A su servicio. ¿Qué necesita?");
                        showToast("Escuchando comandos 🎙️");
                        continue;
                    }
                    if (procesarComando(txt)) { lastWakeMs = now; recognizer.reset(); }
                    continue;
                }

                // Extender ventana de comando si hay flujo activo
                if (voiceFlow.isActive()) {
                    commandModeUntilMs = now + COMMAND_WINDOW_MS;
                }

                if (now > commandModeUntilMs) { mode = Mode.WAKE; continue; }
                if (procesarComando(txt)) {
                    // Si hay flujo activo, mantener modo comando
                    if (!voiceFlow.isActive()) {
                        mode = Mode.WAKE;
                    } else {
                        commandModeUntilMs = now + COMMAND_WINDOW_MS;
                    }
                    recognizer.reset();
                }
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

    // =====================================================================
    //  PROCESAMIENTO DE COMANDOS
    // =====================================================================

    private boolean procesarComando(String c) {
        Log.d(TAG, "Comando: " + c);
        String lc = c.toLowerCase(Locale.ROOT);

        // ── 1. Si hay flujo guiado activo, delegar primero ──
        if (voiceFlow.isActive() && voiceFlow.procesarComando(lc)) {
            return true;
        }

        // ── 2. ACTIVAR / DESACTIVAR ──
        if (lc.contains("desactivar") || lc.contains("apagar asistente") || lc.contains("silencio")) {
            hablar("Entendido, apagando sistemas de voz. Estaré en silencio hasta que ordene activar.");
            assistantEnabled = false;
            if (voiceFlow.isActive()) voiceFlow.cancelar();
            mode = Mode.WAKE;
            return true;
        }

        if (lc.contains("activar") && !assistantEnabled) {
            assistantEnabled = true;
            hablar("Sistemas de voz en línea. ¿Qué orden de navegación tiene en mente?");
            return true;
        }

        // ── 2b. Ir a dormir (Modo WAKE sin desactivar) ──
        if ((containsWakeWord(lc) && lc.contains("adiós") || lc.contains("adios")) && mode == Mode.COMMAND) {
            hablar("Entendido. Quedo a la espera de sus órdenes.");
            return true;
        }

        // ── 3. ¿Dónde estoy? (Lectura de pantalla actual) ──
        if (lc.contains("dónde estoy") || lc.contains("donde estoy")
                || lc.contains("en qué pantalla") || lc.contains("en que pantalla")) {
            leerNombrePantalla();
            return true;
        }

        // ── 4. Léeme la pantalla / Lee la pantalla ──
        if (lc.contains("léeme la pantalla") || lc.contains("lee la pantalla")
                || lc.contains("leeme la pantalla") || lc.contains("leer pantalla")
                || lc.contains("describe la pantalla") || lc.contains("describir pantalla")) {
            leerDescripcionPantalla();
            return true;
        }

        // ── 5. ¿Qué puedo hacer aquí? ──
        if (lc.contains("qué puedo hacer") || lc.contains("que puedo hacer")
                || lc.contains("opciones") || lc.contains("qué hay aquí")
                || lc.contains("que hay aqui")) {
            leerOpcionesPantalla();
            return true;
        }

        // ── 6. Repite / Repetir ──
        if (lc.contains("repite") || lc.contains("repetir") || lc.contains("repítelo")
                || lc.contains("otra vez") || lc.contains("no escuché") || lc.contains("no escuche")) {
            if (ultimoMensaje != null && !ultimoMensaje.isEmpty()) {
                tts.speak(ultimoMensaje, TextToSpeech.QUEUE_FLUSH, null, "rep_" + System.currentTimeMillis());
            } else {
                hablar("No tengo nada que repetir.");
            }
            return true;
        }

        // ── 7. Ir atrás / Volver ──
        if (lc.contains("ir atrás") || lc.contains("ir atras") || lc.contains("volver atrás")
                || lc.contains("volver atras") || lc.contains("regresar")
                || (lc.contains("atrás") && !lc.contains("puedo"))) {
            hablar("Volviendo atrás.");
            irAtras();
            return true;
        }

        // ── 8. Búsqueda de viajes por lenguaje natural ──
        if (lc.contains("quiero ir a") || lc.contains("llévame a") || lc.contains("llevame a")
                || lc.contains("viaje a") || lc.contains("viaje al")
                || lc.contains("viaje hacia") || lc.contains("quiero viajar")
                || lc.contains("buscar viaje a") || lc.contains("ir a")) {
            String destino = extraerDestino(lc);
            if (!destino.isEmpty()) {
                voiceFlow.iniciarBusquedaViaje(destino);
                return true;
            }
        }

        // ── 9. "Ayúdame a reservar un viaje" ──
        if (lc.contains("reservar un viaje") || lc.contains("ayúdame a reservar")
                || lc.contains("ayudame a reservar") || lc.contains("quiero reservar")) {
            hablar("¡Claro! ¿A dónde quieres ir? Di por ejemplo: quiero ir al centro.");
            return true;
        }

        // ── 10. Selección por voz (primero, segundo, etc.) — fuera de flujo ──
        if (lc.contains("primer") || lc.contains("segund") || lc.contains("tercer")
                || lc.contains("cuart") || lc.contains("quint")) {
            if (voiceFlow.isActive()) {
                return voiceFlow.procesarComando(lc);
            }
            hablar("No hay opciones para seleccionar en este momento.");
            return true;
        }

        // ── 11. Cerrar sesión ──
        if (lc.contains("cerrar sesión") || lc.contains("cerrar sesion")
                || lc.contains("cerrar perfil") || lc.contains("salir de la cuenta")
                || lc.contains("cerrar cuenta") || lc.contains("salir de mi cuenta")
                || lc.contains("logout") || lc.contains("log out")) {
            hablar("Cerrando sesión. ¡Hasta pronto!");
            cerrarSesion();
            return true;
        }

        // ── 12. Navegación por voz a secciones ──
        if (lc.contains("buscar viaje") || lc.contains("buscar viajes") || lc.contains("quiero buscar")) {
            hablar("Abriendo búsqueda de viajes."); intentarAbrir(HomePasajero.class); return true; }
        if (lc.contains("mis reservas") || (lc.contains("reserva") && !lc.contains("reservar"))) {
            hablar("Abriendo tus reservas."); intentarAbrir(MisReservasActivity.class); return true; }
        if (lc.contains("publicar") || lc.contains("nuevo viaje") || lc.contains("crear viaje")) {
            hablar("Abriendo publicación de viajes."); intentarAbrir(PublicarRuta.class); return true; }
        if (lc.contains("mis rutas") || (lc.contains("rutas") && !lc.contains("ruta"))) {
            hablar("Abriendo tus rutas."); intentarAbrir(MisRutasActivity.class); return true; }
        if (lc.contains("mis vehiculos") || lc.contains("vehículos") || lc.contains("vehiculos")) {
            hablar("Abriendo tus vehículos."); intentarAbrir(MisVehiculosActivity.class); return true; }
        if (lc.contains("perfil") || lc.contains("mi cuenta")) {
            hablar("Abriendo tu perfil."); intentarAbrir(PerfilUsuario.class); return true; }
        if (lc.contains("mensajes") || lc.contains("chat")) {
            hablar("Abriendo mensajes."); intentarAbrir(Mensajes.class); return true; }
        if (lc.contains("mapa")) {
            hablar("Abriendo el mapa."); intentarAbrir(Mapa.class); return true; }
        if (lc.contains("inicio") || lc.contains("pantalla principal") || lc.contains("home")) {
            SessionManager s = new SessionManager(context);
            if (s.isConductor()) { hablar("Volviendo al inicio."); intentarAbrir(HomeConductor.class); }
            else { hablar("Volviendo al inicio."); intentarAbrir(HomePasajero.class); }
            return true;
        }

        // ── 13. Conversación Formal (Estilo JARVIS) ──
        if (lc.contains("hola") || lc.contains("buenos días") || lc.contains("buenas tardes") || lc.contains("buenas noches")) {
            hablar("Saludos. Estoy a su entera disposición."); return true; }
        if (lc.contains("cómo estás") || lc.contains("como estas")) {
            hablar("Todos los sistemas operan en óptimas condiciones. ¿Cuál es nuestro destino?"); return true; }
        if (lc.contains("quién eres") || lc.contains("quien eres") || lc.contains("como te llamas")) {
            hablar("Soy su asistente virtual integrado en MoviFlex, programado para garantizar una navegación sin contratiempos."); return true; }
        if (lc.contains("qué es moviflex") || lc.contains("que es moviflex")) {
            hablar("MoviFlex es una plataforma diseñada para optimizar los viajes diarios, conectando conductores y pasajeros para reducir la huella de carbono térmica urbana."); return true; }
        if (lc.contains("cómo funciona") || lc.contains("como funciona")) {
            hablar("Analizo las rutas disponibles y gestiono su itinerario, ya sea reservando un viaje seguro o habilitando uno propio."); return true; }
        if (lc.contains("pagos") || lc.contains("cómo pago") || lc.contains("cuánto cuesta")) {
            hablar("Los arreglos financieros se acuerdan previamente, maximizando el ahorro de recursos."); return true; }
        if (lc.contains("seguridad") || lc.contains("es seguro")) {
            hablar("Mis protocolos de seguridad incluyen verificación rigurosa de cada participante en la plataforma. Puede estar tranquilo."); return true; }

        // ── 14. Selección de paradas (desde DetalleViajeActivity) ──
        if (lc.contains("donde me subo") || lc.contains("punto de recogida")
                || lc.contains("seleccionar subida") || lc.contains("donde subo")) {
            Activity act = getCurrentActivity();
            if (act instanceof com.arlys.moviflexx.controller.DetalleViajeActivity) {
                voiceFlow.iniciarSeleccionParada(true);
                return true;
            }
        }
        if (lc.contains("donde me bajo") || lc.contains("punto de bajada")
                || lc.contains("seleccionar bajada") || lc.contains("donde bajo")) {
            Activity act = getCurrentActivity();
            if (act instanceof com.arlys.moviflexx.controller.DetalleViajeActivity) {
                voiceFlow.iniciarSeleccionParada(false);
                return true;
            }
        }

        // ── 15. Ayuda general ──
        if (lc.contains("ayuda") || lc.contains("qué puedes hacer") || lc.contains("que puedes hacer")
                || lc.contains("comandos")) {
            hablar("Puedo hacer muchas cosas. "
                    + "Di: ¿dónde estoy? para saber en qué pantalla estás. "
                    + "Di: léeme la pantalla, para que te describa lo que hay. "
                    + "Di: ¿qué puedo hacer aquí? para conocer las opciones. "
                    + "Di: quiero ir al centro, para buscar viajes. "
                    + "Di: ir atrás, para regresar. "
                    + "Di: repite, para escuchar de nuevo. "
                    + "O di el nombre de una sección como: mis reservas, perfil, mensajes.");
            return true;
        }

        if (lc.contains("gracias")) {
            hablar("Con gusto. ¿Necesitas algo más?"); return true; }
        if (lc.contains("cancelar") || lc.contains("nada") || lc.contains("salir")) {
            hablar("Entendido."); return true; }

        return false;
    }

    // =====================================================================
    //  LECTURA DE PANTALLA Y AYUDA CONTEXTUAL
    // =====================================================================

    /**
     * Lee el nombre de la pantalla actual usando ScreenDescriptor.
     */
    private void leerNombrePantalla() {
        Activity current = MyApplication.getCurrentActivity();
        if (current instanceof ScreenDescriptor) {
            String nombre = ((ScreenDescriptor) current).getNombrePantalla();
            hablar("Estás en la pantalla de " + nombre + ".");
        } else if (current != null) {
            hablar("Estás en " + current.getClass().getSimpleName() + ".");
        } else {
            hablar("No puedo detectar la pantalla actual.");
        }
    }

    /**
     * Lee la descripción completa de lo que hay en la pantalla.
     */
    private void leerDescripcionPantalla() {
        Activity current = MyApplication.getCurrentActivity();
        if (current instanceof ScreenDescriptor) {
            ScreenDescriptor sd = (ScreenDescriptor) current;
            String nombre = sd.getNombrePantalla();
            String desc = sd.getDescripcionPantalla();
            hablar("Estás en " + nombre + ". " + desc);
        } else if (current != null) {
            hablar("Estás en " + current.getClass().getSimpleName()
                    + ". No tengo una descripción detallada de esta pantalla.");
        } else {
            hablar("No puedo detectar la pantalla actual.");
        }
    }

    /**
     * Lee las opciones disponibles en la pantalla actual.
     */
    private void leerOpcionesPantalla() {
        Activity current = MyApplication.getCurrentActivity();
        if (current instanceof ScreenDescriptor) {
            String opciones = ((ScreenDescriptor) current).getOpcionesPantalla();
            hablar(opciones);
        } else {
            hablar("Puedes decir: ir atrás, ir al inicio, buscar viaje, o ayuda.");
        }
    }

    // =====================================================================
    //  ACCIONES DE NAVEGACIÓN
    // =====================================================================

    /**
     * Simula el botón atrás en la Activity visible.
     */
    private void irAtras() {
        new Handler(Looper.getMainLooper()).post(() -> {
            Activity current = MyApplication.getCurrentActivity();
            if (current != null) {
                current.onBackPressed();
            }
        });
    }

    /**
     * Extrae el destino de una frase como "quiero ir al centro" → "centro"
     */
    private String extraerDestino(String lc) {
        String[] prefijos = {
                "quiero ir a ", "quiero ir al ", "quiero viajar a ", "quiero viajar al ",
                "llévame a ", "llévame al ", "llevame a ", "llevame al ",
                "viaje a ", "viaje al ", "viaje hacia ", "viaje hacia el ",
                "buscar viaje a ", "buscar viaje al ",
                "ir a ", "ir al "
        };
        for (String p : prefijos) {
            int idx = lc.indexOf(p);
            if (idx >= 0) {
                String dest = lc.substring(idx + p.length()).trim();
                // Limpiar posibles sufijos
                dest = dest.replace("por favor", "").replace("gracias", "").trim();
                if (!dest.isEmpty()) return dest;
            }
        }
        return "";
    }

    // =====================================================================
    //  ACCIONES EXISTENTES
    // =====================================================================

    private void cerrarSesion() {
        try {
            SessionManager session = new SessionManager(context);
            session.logout();
            Log.d(TAG, "Sesión cerrada correctamente.");
        } catch (Exception e) {
            Log.w(TAG, "cerrarSesion error: " + e.getMessage());
        }
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

    // =====================================================================
    //  UTILIDADES VOSK
    // =====================================================================

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
        return n.contains("movi") || n.contains("mobi") || n.contains("movy") || n.contains("jarvis") || n.contains("asistente");
    }

    // =====================================================================
    //  FLUJO GUIADO — acceso público
    // =====================================================================

    public VoiceFlowManager getVoiceFlow() {
        return voiceFlow;
    }

    // =====================================================================
    //  SHUTDOWN
    // =====================================================================

    public void shutdown() {
        stop();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        isInitialized = false;
        if (voskModel != null) { voskModel.close(); voskModel = null; }
        Log.d(TAG, "VoiceAssistantManager liberado.");
    }
}