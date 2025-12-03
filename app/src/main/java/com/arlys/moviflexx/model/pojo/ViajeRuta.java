package com.arlys.moviflexx.model.pojo;

// Tabla de unión entre Viajes y Rutas
public class ViajeRuta {
    private int idViajes; // FK
    private int idRutas; // FK

    public ViajeRuta(int idViajes, int idRutas) {
        this.idViajes = idViajes;
        this.idRutas = idRutas;
    }

    public void setIdViajes(int idViajes) {
        this.idViajes = idViajes;
    }
    public void setIdRutas(int idRutas) {
        this.idRutas = idRutas;
    }

    public int getIdViajes() {
        return idViajes;
    }
    public int getIdRutas() {
        return idRutas;
    }
}