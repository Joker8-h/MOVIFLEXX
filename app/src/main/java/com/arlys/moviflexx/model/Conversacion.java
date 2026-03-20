package com.arlys.moviflexx.model;

import android.util.Log;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


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
    private String  fotoContacto   = "";
    private long    timestampOrden = 0L;

    public Conversacion() {}

    public static Conversacion fromJson(JSONObject obj, int idUsuarioActual) {
        Conversacion c = new Conversacion();
        if (obj == null) return c;

        Log.d(TAG, "──────────────────────────────────────────");
        Log.d(TAG, "JSON crudo: " + obj.toString());
        Log.d(TAG, "──────────────────────────────────────────");

        // ── ID ────────────────────────────────────────────────────────
        c.id = obj.optLong("id",
                obj.optLong("idConversacion",
                        obj.optLong("conversacionId",
                                obj.optLong("chatId", -1))));

        // ── ÚLTIMO MENSAJE ────────────────────────────────────────────
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

        // Buscar en objeto mensajes[] si existe
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

        // Intentar extraer último mensaje del array "mensajes" si viene en el JSON
        if (c.ultimoMensaje.isEmpty()) {
            org.json.JSONArray mensajesArr = obj.optJSONArray("mensajes");
            if (mensajesArr != null && mensajesArr.length() > 0) {
                JSONObject ultimo = mensajesArr.optJSONObject(mensajesArr.length() - 1);
                if (ultimo != null) {
                    c.ultimoMensaje = primeraNoVacia(
                            ultimo.optString("mensaje",   ""),
                            ultimo.optString("contenido", ""),
                            ultimo.optString("message",   "")
                    );
                    String fechaMensaje = primeraNoVacia(
                            ultimo.optString("fechaEnvio", ""),
                            ultimo.optString("createdAt",  ""),
                            ultimo.optString("timestamp",  "")
                    );
                    if (!fechaMensaje.isEmpty()) {
                        long ts = parsearTimestampStatic(fechaMensaje);
                        if (ts > 0) c.timestampOrden = ts;
                    }
                    int idRemitente = ultimo.optInt("idRemitente",
                            ultimo.optInt("emisorId", -1));
                    c.mio = (idRemitente == idUsuarioActual && idUsuarioActual != -1);
                    c.ultimoMensajeLeidoPorContacto = ultimo.optBoolean("leido", false);
                }
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

        if (c.timestampOrden == 0L && !c.fechaUltimo.isEmpty()) {
            long ts = parsearTimestampStatic(c.fechaUltimo);
            if (ts > 0) c.timestampOrden = ts;
        }

        // ── NO LEÍDOS ─────────────────────────────────────────────────
        c.noLeidos = leerEnteroSeguro(obj,
                "mensajesNoLeidos", "unreadCount", "unread",
                "noLeidos", "no_leidos", "countNoLeidos",
                "unreadMessages", "sinLeer", "pendientes"
        );

        Log.d(TAG, "noLeidos resuelto: " + c.noLeidos);

        // ── EMISOR ────────────────────────────────────────────────────
        if (!c.mio) {
            int idEmisor = leerEnteroSeguro(obj,
                    "idEmisorUltimo", "lastSenderId", "emisorId", "idRemitente");
            c.mio = (idEmisor > 0 && idEmisor == idUsuarioActual);
        }

        // ── LEÍDO POR CONTACTO ────────────────────────────────────────
        if (!c.ultimoMensajeLeidoPorContacto) {
            c.ultimoMensajeLeidoPorContacto =
                    obj.optBoolean("ultimoMensajeLeidoPorContacto",
                            obj.optBoolean("lastMessageRead",
                                    obj.optBoolean("leidoPorContacto", false)));
        }

        // ── NOMBRE + FOTO — Estructura pasajero/conductor ─────────────
        JSONObject pasajeroObj  = obj.optJSONObject("pasajero");
        JSONObject conductorObj = obj.optJSONObject("conductor");
        if (pasajeroObj != null && conductorObj != null) {
            long idPasajero  = obj.optLong("idPasajero",
                    obj.optLong("pasajeroId", leerIdSeguro(pasajeroObj)));
            long idConductor = obj.optLong("idConductor",
                    obj.optLong("conductorId", leerIdSeguro(conductorObj)));

            Log.d(TAG, "idPasajero=" + idPasajero + " idConductor=" + idConductor
                    + " idUsuarioActual=" + idUsuarioActual);

            boolean yoSoyPasajero  = (idPasajero  > 0 && idPasajero  == idUsuarioActual);
            boolean yoSoyConductor = (idConductor > 0 && idConductor == idUsuarioActual);

            if (!yoSoyPasajero && !yoSoyConductor) {
                yoSoyConductor = (idConductor <= 0 && idPasajero > 0);
                yoSoyPasajero  = !yoSoyConductor;
            }

            if (yoSoyPasajero) {
                c.nombreContacto = primeraNoVacia(
                        conductorObj.optString("nombre", ""),
                        conductorObj.optString("name",   ""),
                        "Conductor");
                c.idContacto   = (int) idConductor;
                c.fotoContacto = extraerFoto(conductorObj);
            } else {
                c.nombreContacto = primeraNoVacia(
                        pasajeroObj.optString("nombre", ""),
                        pasajeroObj.optString("name",   ""),
                        "Pasajero");
                c.idContacto   = (int) idPasajero;
                c.fotoContacto = extraerFoto(pasajeroObj);
            }
            Log.d(TAG, "Resultado → contacto: " + c.nombreContacto
                    + " foto=" + c.fotoContacto + " id=" + c.id);
            return c;
        }

        // ── Estructura usuario1/usuario2 ──────────────────────────────
        JSONObject u1 = obj.optJSONObject("usuario1");
        JSONObject u2 = obj.optJSONObject("usuario2");
        if (u1 != null && u2 != null) {
            int id1 = u1.optInt("id", u1.optInt("idUsuarios", -1));
            int id2 = u2.optInt("id", u2.optInt("idUsuarios", -1));
            if (id1 == idUsuarioActual) {
                c.nombreContacto = primeraNoVacia(u2.optString("nombre", ""), "Usuario");
                c.idContacto   = id2;
                c.fotoContacto = extraerFoto(u2);
            } else {
                c.nombreContacto = primeraNoVacia(u1.optString("nombre", ""), "Usuario");
                c.idContacto   = id1;
                c.fotoContacto = extraerFoto(u1);
            }
            return c;
        }

        // ── Estructura plana ──────────────────────────────────────────
        c.nombreContacto = primeraNoVacia(
                obj.optString("nombreContacto",  ""),
                obj.optString("nombre_contacto", ""),
                obj.optString("contactName",     ""),
                obj.optString("nombre",          ""),
                "Usuario");
        c.idContacto = obj.optInt("idContacto", obj.optInt("contactId", -1));

        JSONObject contactoObj = primeraObjNoNula(
                obj.optJSONObject("contacto"),
                obj.optJSONObject("usuario"),
                obj.optJSONObject("user")
        );
        c.fotoContacto = contactoObj != null
                ? extraerFoto(contactoObj)
                : extraerFotoPlana(obj);

        Log.d(TAG, "Estructura plana → contacto: "
                + c.nombreContacto + " foto=" + c.fotoContacto + " id=" + c.id);
        return c;
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private static String extraerFoto(JSONObject u) {
        if (u == null) return "";
        String url = primeraNoVacia(
                u.optString("fotoPerfi",      ""),
                u.optString("fotoPerfil",     ""),
                u.optString("foto",           ""),
                u.optString("photoUrl",       ""),
                u.optString("profilePicture", ""),
                u.optString("avatar",         ""),
                u.optString("imagenPerfil",   ""),
                u.optString("urlFoto",        "")
        );
        Log.d(TAG, "extraerFoto → " + url);
        return url;
    }

    private static String extraerFotoPlana(JSONObject obj) {
        if (obj == null) return "";
        return primeraNoVacia(
                obj.optString("fotoContacto",   ""),
                obj.optString("fotoPerfil",     ""),
                obj.optString("fotoPerfi",      ""),
                obj.optString("foto",           ""),
                obj.optString("photoUrl",       ""),
                obj.optString("contactPhoto",   ""),
                obj.optString("profilePicture", ""),
                obj.optString("avatar",         "")
        );
    }

    private static String primeraNoVacia(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isEmpty() && !v.equals("null")) return v;
        }
        return "";
    }

    private static JSONObject primeraObjNoNula(JSONObject... objs) {
        for (JSONObject o : objs) { if (o != null) return o; }
        return null;
    }

    private static int leerEnteroSeguro(JSONObject obj, String... claves) {
        for (String clave : claves) {
            if (obj.has(clave)) return obj.optInt(clave, 0);
        }
        return 0;
    }

    private static long leerIdSeguro(JSONObject obj) {
        if (obj == null) return -1;
        for (String campo : new String[]{"id","idUsuarios","idUsuario","userId","user_id"}) {
            if (obj.has(campo)) { long val = obj.optLong(campo, -1); if (val > 0) return val; }
        }
        return -1;
    }

    private static long parsearTimestampStatic(String fecha) {
        if (fecha == null || fecha.isEmpty()) return 0L;
        try {
            long num = Long.parseLong(fecha);
            return num < 10_000_000_000L ? num * 1000L : num;
        } catch (NumberFormatException ignored) {}
        String[] fmts = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ssZ"
        };
        for (String f : fmts) {
            try {
                Date d = new SimpleDateFormat(f, Locale.getDefault()).parse(fecha);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return 0L;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public long    getId()                           { return id; }
    public String  getNombreContacto()               { return nombreContacto; }
    public String  getUltimoMensaje()                { return ultimoMensaje; }
    public String  getFechaUltimo()                  { return fechaUltimo; }
    public int     getNoLeidos()                     { return noLeidos; }
    public int     getIdContacto()                   { return idContacto; }
    public String  getFechaUltimoMensaje()           { return fechaUltimo; }
    public int     getMensajesNoLeidos()             { return noLeidos; }
    public boolean isUltimoMensajeMio()              { return mio; }
    public boolean isUltimoMensajeLeidoPorContacto() { return ultimoMensajeLeidoPorContacto; }
    public long    getTimestampOrden()               { return timestampOrden; }
    public String  getFotoContacto()                 { return fotoContacto != null ? fotoContacto : ""; }

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setFotoContacto(String url)            { this.fotoContacto  = url != null ? url : ""; }
    public void setUltimoMensaje(String ultimoMensaje) { this.ultimoMensaje = ultimoMensaje; }
    public void setTimestampOrden(long ts)             { this.timestampOrden = ts; }
    // ← NUEVO: necesario para acumular no leídos al deduplicar por contacto
    public void setMensajesNoLeidos(int cantidad)      { this.noLeidos = Math.max(0, cantidad); }
}