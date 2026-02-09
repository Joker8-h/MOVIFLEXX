package com.arlys.moviflexx.model;

import org.json.JSONObject;

public class Mensaje {

    private String mensaje;
    private String emisor;
    private String hora;

    public Mensaje(String mensaje, String emisor, String hora) {
        this.mensaje = mensaje;
        this.emisor = emisor;
        this.hora = hora;
    }

    public String getMensaje() { return mensaje; }
    public String getEmisor() { return emisor; }
    public String getHora() { return hora; }

    public static Mensaje fromJson(JSONObject json) {
        return new Mensaje(
                json.optString("mensaje"),
                json.optString("emisor"),
                json.optString("hora")
        );
    }
}
