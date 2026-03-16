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

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arlys.moviflexx.controller.BuscarViajesPasajeros;
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


    // Vosk (offline)
    private volatile boolean running = false;
    private Thread worker;
    private Model voskModel;
    private Recognizer recognizer;
    private AudioRecord audioRecord;

    private enum Mode { WAKE, COMMAND }
    private volatile Mode mode = Mode.WAKE;
    private long commandModeUntilMs = 0L;
    private long lastWakeMs = 0L;

    private static final String WAKE_WORD = "movi";
    private static final long WAKE_COOLDOWN_MS = 2500L;
    private static final long COMMAND_WINDOW_MS = 9000L;

    private VoiceAssistantManager(Context context) {
        this.context = context.getApplicationContext();
        initializeTTS();
    }

    public static synchronized VoiceAssistantManager getInstance(Context context) {
        if (instance == null) instance = new VoiceAssistantManager(context);
        return instance;
    }

    private void initializeTTS() {
        tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Locale loc = new Locale("es");
            int result = tts.setLanguage(loc);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback a default locale if Spanish is not available
                tts.setLanguage(Locale.getDefault());
            }
            isInitialized = true;
            setupUtteranceListener();
            configurarVozDiferente();
            Log.d(TAG, "TTS Inicializado correctamente.");
            showToast("Movi está lista para ayudarte 🎙️");
        } else {
            Log.e(TAG, "Fallo al inicializar TTS status: " + status);
        }
    }
    private void configurarVozDiferente() {
        if (tts == null) return;
        try {
            tts.setPitch(1.1f); 
            tts.setSpeechRate(0.95f); 

            android.speech.tts.Voice bestVoice = null;
            for (android.speech.tts.Voice voice : tts.getVoices()) {
                String name = voice.getName().toLowerCase();
                if (name.contains("es")) {
                    if (voice.isNetworkConnectionRequired()) {
                        bestVoice = voice;
                        break; 
                    }
                    if (name.contains("female") || name.contains("femenina") || name.contains("soft")) {
                        bestVoice = voice;
                    }
                }
            }
            if (bestVoice != null) {
                tts.setVoice(bestVoice);
                Log.d(TAG, "Voz seleccionada: " + bestVoice.getName());
            }
        } catch (Exception e) {
            Log.w(TAG, "Error voz: " + e.getMessage());
        }
    }

    private void setupUtteranceListener() {
        if (tts == null) return;
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                isSpeaking = true;
                Log.d(TAG, "TTS Iniciado: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                isSpeaking = false;
                Log.d(TAG, "TTS Finalizado: " + utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                isSpeaking = false;
                Log.e(TAG, "TTS Error: " + utteranceId);
            }
        });
    }

    public void saludarConDatoCurioso(String nombre) {
        String[] datosCuriosos = {
            "¿Sabías que el primer auto del mundo solo alcanzaba los 16 kilómetros por hora?",
            "¿Dato curioso? El semáforo existió antes que los autos, ¡se usaba para los trenes!",
            "¿Sabías que en MoviFlex estamos reduciendo la huella de carbono al compartir viajes?",
            "Un dato interesante: La red de carreteras más larga del mundo es la Panamericana, que une a casi todo el continente.",
            "¿Sabías que compartir viaje puede reducir tu estrés y ayudarte a conocer personas increíbles?",
            "Dato curioso: El GPS que usas hoy fue diseñado originalmente para uso militar, ¡y ahora nos lleva a todos lados!",
            "¿Sabías que el auto más vendido de la historia es el Toyota Corolla?",
            "Dato curioso: El primer viaje largo en automóvil lo hizo una mujer, Bertha Benz, en 1888.",
            "¿Sabías que compartir un auto con tres personas reduce las emisiones por pasajero hasta en un 66%?",
            "Un dato interesante: La primera multa por exceso de velocidad se emitió en 1896, a un auto que iba a 13 kilómetros por hora.",
            "¿Sabías que los primeros autos no tenían volante, sino una palanca como los barcos?",
            "Dato curioso: MoviFlex no solo te ayuda a moverte, sino a crear comunidad en cada viaje.",
            "¿Sabías que el olor a auto nuevo está compuesto por más de 50 compuestos químicos diferentes?",
            "Un dato: En promedio, un automóvil pasa el 95% de su vida útil estacionado. ¡Compartir viajes le da más uso a nuestros autos!",
            "¿Sabías que la palabra 'automóvil' proviene del griego 'autos' que significa 'por sí mismo', y del latín 'mobilis' que significa 'que se mueve'?",
            "Dato curioso sobre viajes: Escuchar tu música favorita o un buen podcast hace que cualquier trayecto parezca más corto.",
            "¿Sabías que la primera radio para autos se inventó en 1929 y costaba un cuarto del valor del vehículo?",
            "Un dato increíble: Si pudieras conducir hacia el espacio, llegarías en solo una hora yendo a 100 kilómetros por hora.",
            "¿Sabías que el cinturón de seguridad se inventó en 1959 y ha salvado más de un millón de vidas?",
            "Dato curioso: Compartir viaje con MoviFlex ayuda a descongestionar el tráfico de nuestra ciudad."
        };
        int index = (int) (Math.random() * datosCuriosos.length);
        String mensaje = "¡Hola, " + nombre + "! Soy Movi, qué gusto verte de nuevo. " + datosCuriosos[index];
        hablarEnCola(mensaje);
    }

    public void hablarEnCola(String texto) {
        if (isInitialized && tts != null) {
            Log.d(TAG, "Hablando (En cola): " + texto);
            // Usamos QUEUE_ADD para que no interrumpa saludos previos si los hay
            tts.speak(texto, TextToSpeech.QUEUE_ADD, null, "moviflex_queue_" + System.currentTimeMillis());
        }
    }

    public void hablar(String texto) {
        if (isInitialized && tts != null) {
            Log.d(TAG, "Hablando: " + texto);
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "moviflex_tts");
        } else {
            Log.w(TAG, "TTS no listo para hablar.");
        }
    }

    /**
     * API actual del proyecto: mantener compatibilidad.
     * Inicia escucha continua (wake word + comando) SOLO con app en foreground.
     */
    public void escuchar() { start(); }

    public synchronized void start() {
        if (running) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Sin permiso RECORD_AUDIO, no se puede iniciar asistente.");
            return;
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

            if (voskModel == null) {
                voskModel = new org.vosk.Model(VoskModelManager.getModelDir(context).getAbsolutePath());
            }
            if (recognizer == null) {
                recognizer = new org.vosk.Recognizer(voskModel, 16000.0f);
            }

            int minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(minBuf, 8192);
            
            Log.d(TAG, "Iniciando AudioRecord...");
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
            );
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Error: AudioRecord no se pudo inicializar. ¿Micrófono en uso?");
                running = false;
                return;
            }
            audioRecord.startRecording();
            Log.d(TAG, "Asistente escuchando (Modo WAKE)...");

            short[] buffer = new short[bufSize / 2];
            boolean wasSpeaking = false;
            long lastSpeakCompleteMs = 0L;

            while (running && !Thread.currentThread().isInterrupted()) {
                int n = audioRecord.read(buffer, 0, buffer.length);
                if (n <= 0) continue;

                long currentMs = System.currentTimeMillis();
                boolean currentlySpeaking = isSpeaking || (tts != null && tts.isSpeaking());
                
                if (currentlySpeaking) {
                    wasSpeaking = true;
                    lastSpeakCompleteMs = currentMs;
                    // Ignoramos completamente el audio mientras habla Movi
                    continue; 
                } else if (currentMs - lastSpeakCompleteMs < 800) {
                    // Esperamos 800 milisegundos extra para que el eco de la bocina se disipe completamente
                    wasSpeaking = true;
                    continue;
                } else if (wasSpeaking) {
                    wasSpeaking = false;
                    // Reseteamos cualquier estado parcial que Vosk haya guardado
                    try { recognizer.reset(); } catch (Exception ignored) {}
                }

                boolean hasResult = recognizer.acceptWaveForm(buffer, n);
                String raw = hasResult ? recognizer.getResult() : recognizer.getPartialResult();
                String normalized = normalizeVoskText(raw);
                if (normalized.isEmpty()) continue;
                Log.d(TAG, "Vosk escuchó: " + normalized);

                long now = currentMs;

                if (mode == Mode.WAKE) {
                    if (containsWakeWord(normalized) && (now - lastWakeMs) > WAKE_COOLDOWN_MS) {
                        lastWakeMs = now;
                        mode = Mode.COMMAND;
                        commandModeUntilMs = now + COMMAND_WINDOW_MS;
                        
                        recognizer.reset();
                        String saludo = "Hola, soy Movi. ¿En qué puedo ayudarte hoy?";
                        hablar(saludo);
                        showToast("Movi te escucha 🎙️");
                        Log.d(TAG, "Wake word detectada!");
                        continue;
                    }


                    if (procesarComando(normalized)) {
                        lastWakeMs = now;
                        recognizer.reset();
                        continue;
                    }
                    continue;
                }

                if (now > commandModeUntilMs) {
                    mode = Mode.WAKE;
                    continue;
                }

                // En modo Comando, intentamos procesar
                if (procesarComando(normalized)) {
                    mode = Mode.WAKE;
                    recognizer.reset();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Fallo asistente offline: " + e.getMessage());
            running = false; // Detener flujo en error fatal
        } finally {
            try { 
                if (audioRecord != null) {
                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) audioRecord.stop();
                    audioRecord.release();
                }
            } catch (Exception ignored) {}
            audioRecord = null;
            try { if (recognizer != null) recognizer.close(); } catch (Exception ignored) {}
            recognizer = null;
            // No cerramos voskModel aquí ya que suele ser costoso recargar, 
            // pero si running es false, se liberará en shutdown().
        }
    }

    private boolean procesarComando(String comando) {
        Log.d(TAG, "Procesando Comando: " + comando);
        String c = comando.toLowerCase(Locale.ROOT);

        if (c.trim().equals("movi") || c.trim().equals("móvil") || c.trim().equals("mobi") || c.contains("desactivar") || c.contains("adiós") || c.contains("adios")) {
            if (mode == Mode.COMMAND) {
                hablar("Entendido, estaré atenta por si me necesitas de nuevo. Solo di Movi.");
                return true; 
            }
            return false; // Ignorar desactivación si ya estamos en modo WAKE
        }

        if (c.contains("buscar viaje") || c.contains("buscar viajes") || c.contains("quiero buscar")) {
            hablar("Claro, abriendo la búsqueda de viajes disponibles para ti.");
            showToast("Buscando viajes...");
            intentarAbrir(BuscarViajesPasajeros.class);
            return true;
        }

        if (c.contains("mis reservas") || (c.contains("reserva") && !c.contains("reservar"))) {
            hablar("Abriendo tus reservas.");
            showToast("Tus reservas");
            intentarAbrir(MisReservasActivity.class);
            return true;
        }

        if (c.contains("publicar") || c.contains("nuevo viaje") || c.contains("crear viaje")) {
            hablar("Abriendo publicación de viajes.");
            showToast("Publicar viaje");
            intentarAbrir(PublicarRuta.class);
            return true;
        }

        if (c.contains("mis rutas") || c.contains("rutas")) {
            hablar("Abriendo tus rutas.");
            showToast("Mis rutas");
            intentarAbrir(MisRutasActivity.class);
            return true;
        }

        if (c.contains("mis vehiculos") || c.contains("mis vehículos") || c.contains("vehiculos") || c.contains("vehículos")) {
            hablar("Abriendo tus vehículos.");
            showToast("Mis vehículos");
            intentarAbrir(MisVehiculosActivity.class);
            return true;
        }

        if (c.contains("perfil") || c.contains("mi cuenta")) {
            hablar("Abriendo tu perfil.");
            showToast("Tu perfil");
            intentarAbrir(PerfilUsuario.class);
            return true;
        }

        if (c.contains("mensajes") || c.contains("chat")) {
            hablar("Abriendo mensajes.");
            showToast("Chats");
            intentarAbrir(Mensajes.class);
            return true;
        }

        if (c.contains("hola") || c.contains("buenos días") || c.contains("buenas tardes")) {
            String[] respuestas = {
                "Hola, ¿cómo estás? Soy Movi, tu asistente. ¿En qué puedo ayudarte?",
                "¡Hola! Qué gusto saludarte. Soy Movi, dime ¿a dónde quieres ir hoy?",
                "Hola. Aquí Movi lista para ayudarte con tu viaje. ¿Qué necesitas?"
            };
            int index = (int) (Math.random() * respuestas.length);
            hablar(respuestas[index]);
            return true;
        }

        if (c.contains("cómo estás") || c.contains("como estas") || c.contains("cómo vas") || c.contains("como vas")) {
            String[] respuestas = {
                "Estoy muy bien, gracias por preguntar. Me llamo Movi y me encanta ayudarte.",
                "¡Excelente! Movi siempre lista para acompañarte en tus rutas.",
                "Todo marcha perfecto. Soy Movi y estoy aquí para que tu viaje sea genial. ¿Y tú qué tal?"
            };
            int index = (int) (Math.random() * respuestas.length);
            hablar(respuestas[index]);
            return true;
        }

        if (c.contains("quién eres") || c.contains("quien eres") || c.contains("cuál es tu nombre") || c.contains("como te llamas")) {
            hablar("Soy Movi, tu asistente inteligente. Mi misión es facilitarte el uso de la app y ayudarte a viajar con total seguridad y comodidad.");
            return true;
        }

        if (c.contains("qué es moviflex") || c.contains("que es moviflex") || c.contains("para qué sirve")) {
            hablar("MoviFlex es una aplicación de transporte compartido que conecta conductores con pasajeros que van hacia el mismo destino, permitiendo ahorrar costos y viajar de forma más amigable.");
            return true;
        }

        if (c.contains("cómo funciona") || c.contains("como funciona") || c.contains("cómo se usa")) {
            hablar("Es muy sencillo. Si eres pasajero, puedes buscar viajes disponibles y reservar tu cupo. Si eres conductor, puedes publicar tu ruta para que otros se unan a ti y compartan los gastos.");
            return true;
        }

        if (c.contains("cómo busco un viaje") || c.contains("buscar un viaje") || c.contains("quiero viajar")) {
            hablar("Para buscar un viaje, solo tienes que decir buscar viaje. Se abrirá un mapa donde podrás ingresar tu destino y ver los conductores disponibles cerca de ti.");
            return true;
        }

        if (c.contains("cómo publico un viaje") || c.contains("publicar un viaje") || c.contains("quiero conducir")) {
            hablar("Si quieres publicar una ruta, di publicar viaje. Deberás ingresar tu origen, destino, precio y cupos disponibles. ¡Así de fácil!");
            return true;
        }

        if (c.contains("gracias") || c.contains("muchas gracias")) {
            hablar("No hay de qué. Es un placer ayudarte. ¿Necesitas algo más?");
            return true;
        }

        if (c.contains("inicio") || c.contains("pantalla principal") || c.contains("home")) {
            SessionManager session = new SessionManager(context);
            if (session.isConductor()) {
                hablar("Volviendo al inicio de conductor.");
                intentarAbrir(HomeConductor.class);
            } else {
                hablar("Volviendo al inicio de pasajero.");
                intentarAbrir(HomePasajero.class);
            }
            return true;
        }

        if (c.contains("ayuda") || c.contains("instrucciones") || c.contains("qué puedes hacer") || c.contains("que puedes hacer")) {
            hablar("Puedes pedirme navegar diciendo ir al mapa o ver perfil. También puedes preguntarme qué es MoviFlex, cómo funciona, o dudas sobre pagos. Solo dime Movi y luego lo que necesites saber.");
            return true;
        }

        if (c.contains("seguridad") || c.contains("es seguro")) {
            hablar("En MoviFlex la seguridad es prioridad. Todos los conductores pasan por una verificación de identidad y los pasajeros pueden calificar cada viaje para mantener una comunidad confiable.");
            return true;
        }

        if (c.contains("pagos") || c.contains("cómo pago") || c.contains("cuánto cuesta")) {
            hablar("Los precios se acuerdan antes de iniciar el viaje y se pueden pagar en efectivo o por los medios que el conductor tenga disponibles. MoviFlex te ayuda a ahorrar compartiendo gastos.");
            return true;
        }

        if (c.contains("mapa") || c.contains("ir al mapa") || c.contains("abre el mapa") || c.contains("donde estoy")) {
            hablar("Abriendo el mapa.");
            showToast("Mapa");
            intentarAbrir(Mapa.class);
            return true;
        }

        if (c.contains("cancelar") || c.contains("nada") || c.contains("salir")) {
            hablar("Entendido.");
            return true;
        }

        return false; // No se detectó comando conocido aún
    }

    private void intentarAbrir(Class<?> cls) {
        try {
            Intent intent = new Intent(context, cls);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error navegando: " + e.getMessage());
        }
    }

    public void shutdown() {
        stop();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (voskModel != null) {
            voskModel.close();
        }
    }

    private void showToast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        );
    }

    private static String normalizeVoskText(String jsonOrText) {
        if (jsonOrText == null) return "";
        String s = jsonOrText.toLowerCase(Locale.ROOT);
        
        if (s.contains("\"partial\"")) {
            String val = extractJsonValue(s, "\"partial\"");
            return val.trim();
        }
        if (s.contains("\"text\"")) {
            String val = extractJsonValue(s, "\"text\"");
            return val.trim();
        }
        
        return s.replace("{", "").replace("}", "").replace("\"", "").trim();
    }

    private static String extractJsonValue(String s, String key) {
        int idx = s.indexOf(key);
        if (idx < 0) return "";
        int colon = s.indexOf(':', idx + key.length());
        if (colon < 0) return "";
        int q1 = s.indexOf('"', colon + 1);
        if (q1 < 0) return "";
        int q2 = s.indexOf('"', q1 + 1);
        if (q2 < 0) return "";
        return s.substring(q1 + 1, q2);
    }

    private static boolean containsWakeWord(String normalizedText) {
        if (normalizedText == null) return false;
        String t = normalizedText.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("ó", "o")
                .replace("í", "i")
                .replace("é", "e")
                .replace("á", "a");
        return t.contains("movi") || t.contains("mobi") || t.contains("movy");
    }

    private static String stripWakeWord(String normalizedText) {
        if (normalizedText == null) return "";
        String t = normalizedText.toLowerCase(Locale.ROOT);
        return t.replace("movi", "")
                .replace("móvil", "")
                .replace("movil", "")
                .replace("mobi", "");
    }
}
