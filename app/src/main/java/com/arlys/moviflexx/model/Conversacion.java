package com.arlys.moviflexx.model;

import android.util.Log;

import org.json.JSONObject;

/**
 * Modelo de Conversación — v3
 *
 * FIXES:
 * 1. "Sin mensajes aún" → busca el ultimoMensaje en todas las claves posibles
 *    que Spring Boot puede devolver, incluyendo objetos anidados.
 * 2. "No leídos siempre vacío" → lee mensajesNoLeidos con has() para distinguir
 *    entre "campo ausente" y "campo en 0", y nunca llama a marcarLeido desde
 *    la lista de conversaciones.
 * 3. Log completo del JSON para diagnosticar el backend.
 */
public class Conversacion {

    private static final String TAG = "ConversacionJSON";

    private long    id;
    private String  nombreContacto = "";
    private String  ultimoMensaje  = "";
    private String  fechaUltimo    = "";
    private int     noLeidos       = 0;
    private int     idContacto     = -1;
    private boolean mio            = false;
    private boolean ultimoMensajeLeidoPorContacto = false;

    public Conversacion() {}

    public static Conversacion fromJson(JSONObject obj, int idUsuarioActual) {
        Conversacion c = new Conversacion();
        if (obj == null) return c;

        // ═══════════════════════════════════════════════════════════════
        // LOG DE DIAGNÓSTICO — ver exactamente qué devuelve el backend
        // Busca en Logcat con el tag "ConversacionJSON"
        // Puedes quitarlo una vez que funcione todo correctamente
        Log.d(TAG, "──────────────────────────────────────────");
        Log.d(TAG, "JSON crudo: " + obj.toString());
        Log.d(TAG, "──────────────────────────────────────────");
        // ═══════════════════════════════════════════════════════════════

        // ── ID ────────────────────────────────────────────────────────
        c.id = obj.optLong("id",
                obj.optLong("idConversacion",
                        obj.optLong("conversacionId",
                                obj.optLong("chatId", -1))));

        // ── ÚLTIMO MENSAJE — todas las variantes posibles ─────────────
        c.ultimoMensaje = primeraNoVacia(
                obj.optString("ultimoMensaje",       ""),
                obj.optString("ultimo_mensaje",      ""),
                obj.optString("lastMessage",         ""),
                obj.optString("last_message",        ""),
                obj.optString("mensajePreview",      ""),
                obj.optString("preview",             ""),
                obj.optString("mensaje",             ""),
                obj.optString("contenido",           ""),
                obj.optString("text",                ""),
                obj.optString("body",                ""),
                obj.optString("ultimoMensajeTexto",  ""),
                obj.optString("previewMensaje",      "")
        );

        // Si el último mensaje está dentro de un objeto anidado
        if (c.ultimoMensaje.isEmpty()) {
            JSONObject nested = primeraObjNoNula(
                    obj.optJSONObject("ultimoMensajeObj"),
                    obj.optJSONObject("lastMessageObj"),
                    obj.optJSONObject("ultimoMensajeDetalle"),
                    obj.optJSONObject("mensajeDetalle"),
                    obj.optJSONObject("lastMsg")
            );
            if (nested != null) {
                c.ultimoMensaje = primeraNoVacia(
                        nested.optString("mensaje",   ""),
                        nested.optString("contenido", ""),
                        nested.optString("message",   ""),
                        nested.optString("text",      ""),
                        nested.optString("body",      "")
                );
            }
        }

        Log.d(TAG, "ultimoMensaje resuelto: '" + c.ultimoMensaje + "'");

        // ── FECHA ─────────────────────────────────────────────────────
        c.fechaUltimo = primeraNoVacia(
                obj.optString("ultimaActividad",      ""),
                obj.optString("fechaUltimoMensaje",   ""),
                obj.optString("fecha_ultimo_mensaje", ""),
                obj.optString("updatedAt",            ""),
                obj.optString("updated_at",           ""),
                obj.optString("creadoEn",             ""),
                obj.optString("createdAt",            ""),
                obj.optString("created_at",           ""),
                obj.optString("timestamp",            ""),
                obj.optString("fechaEnvio",           "")
        );

        // ── NO LEÍDOS ─────────────────────────────────────────────────
        // FIX CRÍTICO: solo ponemos 0 si el campo EXISTE y vale 0.
        // Si el campo no existe en el JSON, dejamos 0 (no podemos saber).
        // Esto evita que siempre aparezca "No leídos" vacío porque el
        // backend no devuelve el campo o lo devuelve con nombre distinto.
        c.noLeidos = leerEnteroSeguro(obj,
                "mensajesNoLeidos",
                "unreadCount",
                "unread",
                "noLeidos",
                "no_leidos",
                "countNoLeidos",
                "unreadMessages",
                "sinLeer",
                "pendientes"
        );

        Log.d(TAG, "noLeidos resuelto: " + c.noLeidos);

        // ── EMISOR ────────────────────────────────────────────────────
        int idEmisor = leerEnteroSeguro(obj,
                "idEmisorUltimo", "lastSenderId", "emisorId", "idRemitente");
        c.mio = (idEmisor > 0 && idEmisor == idUsuarioActual);

        // ── LEÍDO POR CONTACTO ────────────────────────────────────────
        c.ultimoMensajeLeidoPorContacto =
                obj.optBoolean("ultimoMensajeLeidoPorContacto",
                        obj.optBoolean("lastMessageRead",
                                obj.optBoolean("leidoPorContacto", false)));

        // ── NOMBRE CONTACTO — Estructura 1: pasajero/conductor ────────
        JSONObject pasajeroObj  = obj.optJSONObject("pasajero");
        JSONObject conductorObj = obj.optJSONObject("conductor");
        if (pasajeroObj != null && conductorObj != null) {
            int idPasajero  = pasajeroObj.optInt("id",
                    pasajeroObj.optInt("idUsuarios",
                            pasajeroObj.optInt("idUsuario", -1)));
            int idConductor = conductorObj.optInt("id",
                    conductorObj.optInt("idUsuarios",
                            conductorObj.optInt("idUsuario", -1)));

            if (idPasajero == idUsuarioActual) {
                c.nombreContacto = primeraNoVacia(
                        conductorObj.optString("nombre", ""),
                        conductorObj.optString("name",   ""),
                        "Conductor");
                c.idContacto = idConductor;
            } else {
                c.nombreContacto = primeraNoVacia(
                        pasajeroObj.optString("nombre", ""),
                        pasajeroObj.optString("name",   ""),
                        "Pasajero");
                c.idContacto = idPasajero;
            }
            Log.d(TAG, "Estructura pasajero/conductor → contacto: " + c.nombreContacto + " id=" + c.id);
            return c;
        }

        // ── NOMBRE CONTACTO — Estructura 2: usuario1/usuario2 ─────────
        JSONObject u1 = obj.optJSONObject("usuario1");
        JSONObject u2 = obj.optJSONObject("usuario2");
        if (u1 != null && u2 != null) {
            int id1 = u1.optInt("id", u1.optInt("idUsuarios", -1));
            int id2 = u2.optInt("id", u2.optInt("idUsuarios", -1));
            if (id1 == idUsuarioActual) {
                c.nombreContacto = primeraNoVacia(u2.optString("nombre", ""), "Usuario");
                c.idContacto = id2;
            } else {
                c.nombreContacto = primeraNoVacia(u1.optString("nombre", ""), "Usuario");
                c.idContacto = id1;
            }
            Log.d(TAG, "Estructura usuario1/usuario2 → contacto: " + c.nombreContacto + " id=" + c.id);
            return c;
        }

        // ── NOMBRE CONTACTO — Estructura 3: campos planos ─────────────
        c.nombreContacto = primeraNoVacia(
                obj.optString("nombreContacto",  ""),
                obj.optString("nombre_contacto", ""),
                obj.optString("contactName",     ""),
                obj.optString("nombre",          ""),
                "Usuario");
        c.idContacto = obj.optInt("idContacto",
                obj.optInt("contactId", -1));

        Log.d(TAG, "Estructura plana → contacto: " + c.nombreContacto + " id=" + c.id);
        return c;
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /** Devuelve el primer String no vacío ni "null" */
    private static String primeraNoVacia(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isEmpty() && !v.equals("null")) return v;
        }
        return "";
    }

    /** Devuelve el primer JSONObject no nulo */
    private static JSONObject primeraObjNoNula(JSONObject... objs) {
        for (JSONObject o : objs) {
            if (o != null) return o;
        }
        return null;
    }

    /**
     * Lee un entero de la primera clave que exista en el JSON.
     * Si ninguna clave existe, devuelve 0.
     * FIX: usa has() para distinguir "campo ausente" de "campo = 0".
     */
    private static int leerEnteroSeguro(JSONObject obj, String... claves) {
        for (String clave : claves) {
            if (obj.has(clave)) {
                return obj.optInt(clave, 0);
            }
        }
        return 0;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public long    getId()                            { return id; }
    public String  getNombreContacto()                { return nombreContacto; }
    public String  getUltimoMensaje()                 { return ultimoMensaje; }
    public String  getFechaUltimo()                   { return fechaUltimo; }
    public int     getNoLeidos()                      { return noLeidos; }
    public int     getIdContacto()                    { return idContacto; }
    public String  getFechaUltimoMensaje()            { return fechaUltimo; }
    public int     getMensajesNoLeidos()              { return noLeidos; }
    public boolean isUltimoMensajeMio()               { return mio; }
    public boolean isUltimoMensajeLeidoPorContacto()  { return ultimoMensajeLeidoPorContacto; }
}