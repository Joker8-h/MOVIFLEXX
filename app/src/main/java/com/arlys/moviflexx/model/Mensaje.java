package com.arlys.moviflexx.model;

import org.json.JSONObject;

/**
 * Modelo de mensaje de chat.
 * Columnas reales BD: idMensaje | idConversacion | idRemitente | mensaje | tipo | fechaEnvio | leido
 */
public class Mensaje {

    private long    id            = -1;
    private String  contenido     = "";
    private int     idEmisor      = -1;
    private String  nombreEmisor  = "";
    private String  fechaEnvio    = "";
    private boolean leido         = false;

    // Flags locales de UI — nunca vienen del servidor
    private boolean esPropio      = false;
    private boolean enviando      = false;
    private boolean fallido       = false;
    private boolean tipoAudio     = false;

    // Ruta local del archivo de audio grabado (solo existe en el dispositivo)
    private String  rutaAudioLocal = null;

    public Mensaje() {}

    public static Mensaje fromJson(JSONObject obj, int idUsuarioActual) {
        Mensaje m = new Mensaje();
        if (obj == null) return m;

        m.id = obj.optLong("idMensaje", obj.optLong("id", obj.optLong("mensajeId", -1)));
        m.contenido  = obj.optString("mensaje",
                obj.optString("contenido", obj.optString("message", "")));
        m.fechaEnvio = obj.optString("fechaEnvio",
                obj.optString("creadoEn",
                        obj.optString("createdAt",
                                obj.optString("timestamp", ""))));

        // ── Leer leido del backend — NO forzar nunca ──
        if (obj.has("leido")) {
            try { m.leido = obj.getBoolean("leido"); }
            catch (Exception e) { m.leido = obj.optInt("leido", 0) == 1; }
        }

        m.tipoAudio = obj.optBoolean("tipoAudio", false);
        if ("AUDIO".equalsIgnoreCase(obj.optString("tipo", ""))) m.tipoAudio = true;

        String urlRemota = obj.optString("urlAudio", "");
        if (!urlRemota.isEmpty()) m.rutaAudioLocal = urlRemota;

        JSONObject emisorObj = obj.optJSONObject("emisor");
        if (emisorObj != null) {
            m.idEmisor     = emisorObj.optInt("id",
                    emisorObj.optInt("idUsuarios",
                            emisorObj.optInt("idUsuario", -1)));
            m.nombreEmisor = emisorObj.optString("nombre", "");
        }
        if (m.idEmisor == -1)
            m.idEmisor = obj.optInt("idRemitente",
                    obj.optInt("emisorId",
                            obj.optInt("idEmisor",
                                    obj.optInt("remitenteId", -1))));
        if (m.nombreEmisor.isEmpty())
            m.nombreEmisor = obj.optString("nombreEmisor",
                    obj.optString("nombreRemitente", ""));

        m.esPropio = (m.idEmisor == idUsuarioActual && idUsuarioActual != -1);
        // ← NO forzar leido=true aquí aunque sea propio
        // El campo leido real viene del backend y refleja si el CONTACTO lo leyó

        return m;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public long    getId()              { return id; }
    public String  getContenido()       { return contenido; }
    public int     getIdEmisor()        { return idEmisor; }
    public String  getNombreEmisor()    { return nombreEmisor; }
    public String  getFechaEnvio()      { return fechaEnvio; }
    public boolean isLeido()            { return leido; }
    public boolean isEsPropio()         { return esPropio; }
    public boolean isEnviando()         { return enviando; }
    public boolean isFallido()          { return fallido; }
    public boolean isTipoAudio()        { return tipoAudio; }
    public String  getRutaAudioLocal()  { return rutaAudioLocal; }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setId(long v)               { this.id = v; }
    public void setContenido(String v)      { this.contenido = v != null ? v : ""; }
    public void setIdEmisor(int v)          { this.idEmisor = v; }
    public void setNombreEmisor(String v)   { this.nombreEmisor = v != null ? v : ""; }
    public void setFechaEnvio(String v)     { this.fechaEnvio = v != null ? v : ""; }
    public void setLeido(boolean v)         { this.leido = v; }
    public void setEsPropio(boolean v)      { this.esPropio = v; }
    public void setEnviando(boolean v)      { this.enviando = v; }
    public void setFallido(boolean v)       { this.fallido = v; }
    public void setTipoAudio(boolean v)     { this.tipoAudio = v; }
    public void setRutaAudioLocal(String v) { this.rutaAudioLocal = v; }
}