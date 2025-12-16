package com.arlys.moviflexx.model;

public class Conversacion {

    private String nombre;
    private String mensaje;
    private String hora;

    public Conversacion(String nombre, String mensaje, String hora) {
        this.nombre = nombre;
        this.mensaje = mensaje;
        this.hora = hora;
    }

    public String getNombre() {
        return nombre;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getHora() {
        return hora;
    }
}
