package com.arlys.moviflexx.model;

import org.json.JSONObject;

public class Conversacion {

    private long   id;
    private String nombreContacto = "";
    private String ultimoMensaje  = "";
    private String fechaUltimo    = "";
    private int    noLeidos       = 0;
    private int    idContacto     = -1;

    public Conversacion() {}

    public static Conversacion fromJson(JSONObject obj, int idUsuarioActual) {
        Conversacion c = new Conversacion();
        if (obj == null) return c;

        c.id = obj.optLong("id",
                obj.optLong("idConversacion",
                        obj.optLong("conversacionId", -1)));

        c.ultimoMensaje = obj.optString("ultimoMensaje",
                obj.optString("lastMessage",
                        obj.optString("mensaje", "")));

        c.fechaUltimo = obj.optString("ultimaActividad",
                obj.optString("updatedAt",
                        obj.optString("creadoEn",
                                obj.optString("createdAt", ""))));

        c.noLeidos = obj.optInt("mensajesNoLeidos",
                obj.optInt("unreadCount", 0));

        // Estructura 1: { pasajero:{...}, conductor:{...} }
        JSONObject pasajeroObj  = obj.optJSONObject("pasajero");
        JSONObject conductorObj = obj.optJSONObject("conductor");
        if (pasajeroObj != null && conductorObj != null) {
            int idPasajero  = pasajeroObj.optInt("id",  pasajeroObj.optInt("idUsuarios", -1));
            int idConductor = conductorObj.optInt("id", conductorObj.optInt("idUsuarios", -1));
            if (idPasajero == idUsuarioActual) {
                c.nombreContacto = conductorObj.optString("nombre",
                        conductorObj.optString("name", "Conductor"));
                c.idContacto     = idConductor;
            } else {
                c.nombreContacto = pasajeroObj.optString("nombre",
                        pasajeroObj.optString("name", "Pasajero"));
                c.idContacto     = idPasajero;
            }
            return c;
        }

        // Estructura 2: { usuario1:{...}, usuario2:{...} }
        JSONObject u1 = obj.optJSONObject("usuario1");
        JSONObject u2 = obj.optJSONObject("usuario2");
        if (u1 != null && u2 != null) {
            int id1 = u1.optInt("id", u1.optInt("idUsuarios", -1));
            int id2 = u2.optInt("id", u2.optInt("idUsuarios", -1));
            if (id1 == idUsuarioActual) {
                c.nombreContacto = u2.optString("nombre", "Usuario");
                c.idContacto     = id2;
            } else {
                c.nombreContacto = u1.optString("nombre", "Usuario");
                c.idContacto     = id1;
            }
            return c;
        }

        // Estructura 3: campo plano
        c.nombreContacto = obj.optString("nombreContacto",
                obj.optString("contactName", "Usuario"));
        c.idContacto     = obj.optInt("idContacto", -1);
        return c;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public long   getId()             { return id; }
    public String getNombreContacto() { return nombreContacto; }
    public String getUltimoMensaje()  { return ultimoMensaje; }
    public String getFechaUltimo()    { return fechaUltimo; }
    public int    getNoLeidos()       { return noLeidos; }
    public int    getIdContacto()     { return idContacto; }
}