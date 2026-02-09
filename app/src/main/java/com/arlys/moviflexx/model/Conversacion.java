package com.arlys.moviflexx.model;

import org.json.JSONObject;

public class Conversacion {

    private String id;
    private String nombre;
    private String mensaje;
    private String hora;

    public Conversacion(String id, String nombre, String mensaje, String hora) {
        this.id = id;
        this.nombre = nombre;
        this.mensaje = mensaje;
        this.hora = hora;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getMensaje() { return mensaje; }
    public String getHora() { return hora; }

    public static Conversacion fromJson(JSONObject json) {
        if (json == null) return null;

        return new Conversacion(
                json.optString("id"),
                json.optString("nombre"),
                json.optString("ultimoMensaje"),
                json.optString("hora")
        );
    }
}
