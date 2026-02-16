package com.arlys.moviflexx.model;

import org.json.JSONObject;

public class Mensaje {

    private long    id;
    private String  contenido    = "";
    private int     idEmisor     = -1;
    private String  nombreEmisor = "";
    private String  fechaEnvio   = "";

    // Flags de UI — no vienen del servidor
    private boolean esPropio;
    private boolean enviando;
    private boolean fallido;

    public Mensaje() {}

    // ─── fromJson con idUsuarioActual ─────────────────────────────────────────
    public static Mensaje fromJson(JSONObject obj, int idUsuarioActual) {
        Mensaje m = new Mensaje();
        if (obj == null) return m;

        m.id = obj.optLong("id",
                obj.optLong("idMensaje",
                        obj.optLong("mensajeId", -1)));

        m.contenido = obj.optString("mensaje",
                obj.optString("contenido",
                        obj.optString("text",
                                obj.optString("message", ""))));

        m.fechaEnvio = obj.optString("creadoEn",
                obj.optString("createdAt",
                        obj.optString("fechaEnvio",
                                obj.optString("timestamp", ""))));

        // Emisor como objeto anidado
        JSONObject emisorObj = obj.optJSONObject("emisor");
        if (emisorObj != null) {
            m.idEmisor    = emisorObj.optInt("id",
                    emisorObj.optInt("idUsuarios",
                            emisorObj.optInt("idUsuario", -1)));
            m.nombreEmisor = emisorObj.optString("nombre", "");
        }

        // Emisor como campo plano
        if (m.idEmisor == -1) {
            m.idEmisor = obj.optInt("emisorId",
                    obj.optInt("idEmisor",
                            obj.optInt("remitenteId",
                                    obj.optInt("userId", -1))));
        }
        if (m.nombreEmisor.isEmpty()) {
            m.nombreEmisor = obj.optString("nombreEmisor",
                    obj.optString("nombreRemitente", ""));
        }

        m.esPropio = (m.idEmisor == idUsuarioActual);
        return m;
    }

    // ─── fromJson sin idUsuarioActual (compatibilidad) ────────────────────────
    public static Mensaje fromJson(JSONObject obj) {
        return fromJson(obj, -1);
    }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public long    getId()           { return id; }
    public String  getContenido()    { return contenido; }
    public int     getIdEmisor()     { return idEmisor; }
    public String  getNombreEmisor() { return nombreEmisor; }
    public String  getFechaEnvio()   { return fechaEnvio; }
    public boolean isEsPropio()      { return esPropio; }
    public boolean isEnviando()      { return enviando; }
    public boolean isFallido()       { return fallido; }

    // ─── Setters ─────────────────────────────────────────────────────────────
    public void setId(long id)                { this.id = id; }
    public void setContenido(String c)        { this.contenido = c; }
    public void setIdEmisor(int id)           { this.idEmisor = id; }
    public void setNombreEmisor(String n)     { this.nombreEmisor = n; }
    public void setFechaEnvio(String f)       { this.fechaEnvio = f; }
    public void setEsPropio(boolean esPropio) { this.esPropio = esPropio; }
    public void setEnviando(boolean enviando) { this.enviando = enviando; }
    public void setFallido(boolean fallido)   { this.fallido = fallido; }
}