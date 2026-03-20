package com.arlys.moviflexx.model.voice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.VoiceAssistantManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maneja flujos guiados multi-paso por voz para el asistente Movi.
 * Ejemplo: "Quiero ir al centro" → busca viajes → lee resultados → selección → reserva.
 */
public class VoiceFlowManager {

    private static final String TAG = "VoiceFlowManager";

    public enum FlowState {
        IDLE,
        AWAITING_DESTINATION,
        SEARCHING,
        READING_OPTIONS,
        AWAITING_SELECTION,
        AWAITING_PICKUP_SELECTION,
        AWAITING_DROPOFF_SELECTION,
        AWAITING_CONFIRMATION,
        PROCESSING
    }

    private FlowState state = FlowState.IDLE;
    private final Context context;
    private final VoiceAssistantManager assistant;

    // Datos del flujo
    private final List<JSONObject> viajesEncontrados = new ArrayList<>();
    private JSONObject viajeSeleccionado = null;
    private int indiceSeleccionado = -1;
    private String destinoBuscado = "";

    // Paradas del viaje seleccionado (para selección de subida/bajada)
    private final List<String> nombresParadas = new ArrayList<>();
    private int indiceSubida = -1;
    private int indiceBajada = -1;

    public VoiceFlowManager(Context context, VoiceAssistantManager assistant) {
        this.context = context.getApplicationContext();
        this.assistant = assistant;
    }

    public FlowState getState() {
        return state;
    }

    public boolean isActive() {
        return state != FlowState.IDLE;
    }

    /**
     * Cancela el flujo actual y vuelve a IDLE.
     */
    public void cancelar() {
        state = FlowState.IDLE;
        viajesEncontrados.clear();
        nombresParadas.clear();
        viajeSeleccionado = null;
        indiceSeleccionado = -1;
        destinoBuscado = "";
        indiceSubida = -1;
        indiceBajada = -1;
    }

    /**
     * Actualiza la lista de paradas disponibles para el viaje actual.
     */
    public void setNombresParadas(List<String> paradas) {
        this.nombresParadas.clear();
        if (paradas != null) {
            this.nombresParadas.addAll(paradas);
        }
    }

    /**
     * Inicia el proceso de selección de parada de subida o bajada.
     */
    public void iniciarSeleccionParada(boolean esSubida) {
        if (nombresParadas.isEmpty()) {
            assistant.hablar("No hay paradas registradas para este viaje todavía. Espera un momento mientras se cargan.");
            return;
        }

        state = esSubida ? FlowState.AWAITING_PICKUP_SELECTION : FlowState.AWAITING_DROPOFF_SELECTION;
        leerParadas(esSubida);
    }

    private void leerParadas(boolean esSubida) {
        String intro = esSubida ? "¿Dónde te vas a subir? " : "¿Dónde te vas a bajar? ";
        assistant.hablar(intro + "Estas son las paradas disponibles:");

        for (int i = 0; i < nombresParadas.size(); i++) {
            final int num = i + 1;
            final String nombre = nombresParadas.get(i);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                assistant.hablarEnCola(num + ": " + nombre);
                if (num == nombresParadas.size()) {
                    assistant.hablarEnCola("Dime el número de la parada o el nombre.");
                }
            }, (long) i * 600);
        }
    }

    /**
     * Inicia un flujo de búsqueda de viaje por voz.
     * @param destino el destino que el usuario mencionó
     */
    public void iniciarBusquedaViaje(String destino) {
        destinoBuscado = destino;
        state = FlowState.SEARCHING;
        viajesEncontrados.clear();

        assistant.hablar("Entendido. Buscando viajes hacia " + destino + ".");

        String url = Constantes.buscarViajes()
                + "?estado=DISPONIBLE"
                + "&parada=" + android.net.Uri.encode(destino)
                + "&radioKm=15";

        ConexionApi.getInstance(context).getArray(url,
                response -> {
                    procesarResultadosViajes(response);
                },
                error -> {
                    // Fallback: buscar todos y filtrar por nombre
                    ConexionApi.getInstance(context).getArray(
                            Constantes.VIAJES + "?estado=DISPONIBLE",
                            fallbackResp -> procesarResultadosViajes(fallbackResp),
                            fallbackErr -> {
                                assistant.hablar("No pude conectarme al servidor. Intenta de nuevo más tarde.");
                                cancelar();
                            }
                    );
                }
        );
    }

    private void procesarResultadosViajes(JSONArray response) {
        viajesEncontrados.clear();
        if (response == null || response.length() == 0) {
            assistant.hablar("No encontré viajes disponibles hacia " + destinoBuscado
                    + ". Puedes intentar con otro destino.");
            cancelar();
            return;
        }

        // Filtrar por destino si es posible
        for (int i = 0; i < response.length() && viajesEncontrados.size() < 5; i++) {
            JSONObject viaje = response.optJSONObject(i);
            if (viaje == null) continue;

            if (destinoBuscado == null || destinoBuscado.isEmpty()) {
                viajesEncontrados.add(viaje);
            } else {
                String busqueda = destinoBuscado.toLowerCase(java.util.Locale.ROOT);
                String origen   = viaje.optString("origen", "").toLowerCase(java.util.Locale.ROOT);
                String destino  = viaje.optString("destino", "").toLowerCase(java.util.Locale.ROOT);
                JSONObject ruta = viaje.optJSONObject("ruta");
                String nomRuta  = (ruta != null) ? ruta.optString("nombre", "").toLowerCase(java.util.Locale.ROOT) : "";

                if (origen.contains(busqueda) || destino.contains(busqueda) || nomRuta.contains(busqueda)) {
                    viajesEncontrados.add(viaje);
                }
            }
        }

        if (viajesEncontrados.isEmpty()) {
            assistant.hablar("No encontré viajes disponibles hacia " + destinoBuscado + ".");
            cancelar();
            return;
        }

        state = FlowState.READING_OPTIONS;
        leerViajesEncontrados();
    }

    private void leerViajesEncontrados() {
        int total = viajesEncontrados.size();
        assistant.hablar("He encontrado " + total + (total == 1 ? " viaje disponible." : " viajes disponibles."));

        Handler h = new Handler(Looper.getMainLooper());
        for (int i = 0; i < total; i++) {
            final int idx = i;
            h.postDelayed(() -> {
                JSONObject viaje = viajesEncontrados.get(idx);
                String descripcion = describirViaje(viaje, idx + 1);
                assistant.hablarEnCola(descripcion);

                // Después de leer el último, preguntar
                if (idx == viajesEncontrados.size() - 1) {
                    h.postDelayed(() -> {
                        state = FlowState.AWAITING_SELECTION;
                        assistant.hablarEnCola("¿Cuál viaje deseas? Di el primero, el segundo, y así sucesivamente.");
                    }, 1500);
                }
            }, (long) i * 800);
        }
    }

    private String describirViaje(JSONObject viaje, int numero) {
        String[] ordinal = {"", "El primer", "El segundo", "El tercer", "El cuarto", "El quinto"};
        String ord = numero <= 5 ? ordinal[numero] : "El viaje número " + numero;

        String origen = viaje.optString("origen", viaje.optString("puntoOrigen", ""));
        String destino = viaje.optString("destino", viaje.optString("puntoDestino", ""));

        // Intentar obtener desde ruta
        JSONObject ruta = viaje.optJSONObject("ruta");
        if (ruta != null) {
            if (origen.isEmpty()) origen = ruta.optString("origen", "");
            if (destino.isEmpty()) destino = ruta.optString("destino", ruta.optString("nombre", ""));
        }

        double precio = viaje.optDouble("precio", viaje.optDouble("costoCombustible", 0));
        String hora = viaje.optString("horaSalida", viaje.optString("fechaHoraSalida", ""));
        int cupos = viaje.optInt("asientosDisponibles", viaje.optInt("cupos", 0));

        StringBuilder sb = new StringBuilder();
        sb.append(ord).append(" viaje");

        if (!destino.isEmpty()) {
            sb.append(" va hacia ").append(destino);
        }
        if (!hora.isEmpty()) {
            String horaCorta = hora.replace("T", " ");
            if (horaCorta.length() > 16) horaCorta = horaCorta.substring(0, 16);
            sb.append(", sale a las ").append(horaCorta);
        }
        if (precio > 0) {
            sb.append(", por ").append(String.format(Locale.getDefault(), "%,.0f", precio)).append(" pesos");
        }
        if (cupos > 0) {
            sb.append(", con ").append(cupos).append(cupos == 1 ? " cupo disponible" : " cupos disponibles");
        }
        sb.append(".");

        return sb.toString();
    }

    /**
     * Procesa un comando dentro del flujo activo.
     * @return true si el flujo consumió el comando, false si no aplica
     */
    public boolean procesarComando(String comando) {
        if (state == FlowState.IDLE) return false;

        String lc = comando.toLowerCase(Locale.ROOT);

        // Cancelar flujo
        if (lc.contains("cancelar") || lc.contains("salir") || lc.contains("nada") || lc.contains("no quiero")) {
            assistant.hablar("Entendido, cancelo la búsqueda.");
            cancelar();
            return true;
        }

        switch (state) {
            case AWAITING_PICKUP_SELECTION:
                return procesarSeleccionParada(lc, true);

            case AWAITING_DROPOFF_SELECTION:
                return procesarSeleccionParada(lc, false);

            case AWAITING_CONFIRMATION:
                return procesarConfirmacion(lc);

            case READING_OPTIONS:
                // Si está leyendo y el usuario dice algo, esperar
                if (lc.contains("repite") || lc.contains("repetir")) {
                    leerViajesEncontrados();
                    return true;
                }
                // Puede seleccionar mientras se leen
                return procesarSeleccion(lc);

            default:
                return false;
        }
    }

    private boolean procesarSeleccion(String lc) {
        int seleccion = extraerIndice(lc);

        if (seleccion < 0 || seleccion >= viajesEncontrados.size()) {
            if (seleccion >= 0) {
                assistant.hablar("Solo hay " + viajesEncontrados.size() + " viajes. Di un número válido.");
            }
            return seleccion >= 0;
        }

        indiceSeleccionado = seleccion;
        viajeSeleccionado = viajesEncontrados.get(seleccion);

        String destino = viajeSeleccionado.optString("destino",
                viajeSeleccionado.optString("puntoDestino", "el destino"));
        JSONObject ruta = viajeSeleccionado.optJSONObject("ruta");
        if (ruta != null && destino.isEmpty()) {
            destino = ruta.optString("destino", ruta.optString("nombre", "el destino"));
        }

        assistant.hablar("Has seleccionado el viaje hacia " + destino + ". ¿Deseas reservar este viaje? Di sí o no.");
        state = FlowState.AWAITING_CONFIRMATION;
        return true;
    }

    private boolean procesarSeleccionParada(String lc, boolean esSubida) {
        int seleccion = extraerIndice(lc);

        // También intentar buscar por nombre si no es un número claro
        if (seleccion < 0) {
            for (int i = 0; i < nombresParadas.size(); i++) {
                if (lc.contains(nombresParadas.get(i).toLowerCase(Locale.ROOT))) {
                    seleccion = i;
                    break;
                }
            }
        }

        if (seleccion < 0 || seleccion >= nombresParadas.size()) {
            if (lc.contains("parada") || lc.contains("esta")) {
                assistant.hablar("No te entendí. Di el número de la parada.");
                return true;
            }
            return false;
        }

        Activity act = assistant.getCurrentActivity();
        if (!(act instanceof com.arlys.moviflexx.controller.DetalleViajeActivity)) {
            assistant.hablar("Parece que ya no estás en la pantalla de detalles del viaje.");
            cancelar();
            return true;
        }

        com.arlys.moviflexx.controller.DetalleViajeActivity detalle = (com.arlys.moviflexx.controller.DetalleViajeActivity) act;

        if (esSubida) {
            indiceSubida = seleccion;
            assistant.hablar("Punto de subida seleccionado: " + nombresParadas.get(seleccion) + ". Ahora dime, ¿dónde te vas a bajar?");
            // Notificar a la activity
            detalle.runOnUiThread(() -> detalle.seleccionarParadaPorVoz(indiceSubida, true));
            state = FlowState.AWAITING_DROPOFF_SELECTION;
            leerParadas(false);
        } else {
            indiceBajada = seleccion;
            assistant.hablar("Punto de bajada seleccionado: " + nombresParadas.get(seleccion) + ". ¿Confirmas tu reserva? Di sí o no.");
            // Notificar a la activity
            detalle.runOnUiThread(() -> detalle.seleccionarParadaPorVoz(indiceBajada, false));
            state = FlowState.AWAITING_CONFIRMATION;
        }

        return true;
    }

    private int extraerIndice(String lc) {
        if (lc.contains("primer") || lc.contains("uno") || lc.contains("1")) return 0;
        if (lc.contains("segund") || lc.contains("dos") || lc.contains("2")) return 1;
        if (lc.contains("tercer") || lc.contains("tres") || lc.contains("3")) return 2;
        if (lc.contains("cuart") || lc.contains("cuatro") || lc.contains("4")) return 3;
        if (lc.contains("quint") || lc.contains("cinco") || lc.contains("5")) return 4;
        if (lc.contains("sext") || lc.contains("seis") || lc.contains("6")) return 5;
        if (lc.contains("séptim") || lc.contains("siete") || lc.contains("7")) return 6;
        if (lc.contains("octav") || lc.contains("ocho") || lc.contains("8")) return 7;
        if (lc.contains("noven") || lc.contains("nueve") || lc.contains("9")) return 8;
        if (lc.contains("décim") || lc.contains("diez") || lc.contains("10")) return 9;
        return -1;
    }

    private boolean procesarConfirmacion(String lc) {
        if (lc.contains("sí") || lc.contains("si") || lc.contains("confirmar") || lc.contains("reservar")
                || lc.contains("acepto") || lc.contains("claro") || lc.contains("dale")) {

            Activity act = assistant.getCurrentActivity();
            if (act instanceof com.arlys.moviflexx.controller.DetalleViajeActivity) {
                com.arlys.moviflexx.controller.DetalleViajeActivity detalle = (com.arlys.moviflexx.controller.DetalleViajeActivity) act;
                assistant.hablar("Entendido. Procesando tu reserva...");
                detalle.runOnUiThread(detalle::confirmarReservaPorVoz);
                cancelar();
                return true;
            }

            abrirDetalleViaje();
            return true;
        }
        if (lc.contains("no") || lc.contains("cancelar") || lc.contains("nada")) {
            assistant.hablar("Entendido. ¿Quieres escuchar los viajes de nuevo? Di repite, o cancela para salir.");
            state = FlowState.AWAITING_SELECTION;
            return true;
        }
        assistant.hablar("Di sí para reservar o no para cancelar.");
        return true;
    }

    private void abrirDetalleViaje() {
        if (viajeSeleccionado == null) {
            assistant.hablar("Hubo un error. Intenta de nuevo.");
            cancelar();
            return;
        }

        int idViaje = viajeSeleccionado.optInt("idViaje",
                viajeSeleccionado.optInt("idViajes",
                        viajeSeleccionado.optInt("id", 0)));

        if (idViaje == 0) {
            assistant.hablar("No pude obtener el identificador del viaje. Intenta de nuevo.");
            cancelar();
            return;
        }

        state = FlowState.PROCESSING;
        assistant.hablar("Abriendo el detalle del viaje para que puedas reservar.");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent intent = new Intent(context,
                        com.arlys.moviflexx.controller.DetalleViajeActivity.class);
                intent.putExtra("ID_VIAJE", idViaje);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error abriendo DetalleViaje: " + e.getMessage());
                assistant.hablar("Hubo un error al abrir el viaje.");
            }
            cancelar();
        }, 1200);
    }
}
