package com.arlys.moviflexx.model.pojo;

public class Rutas {
    private int idRutas;
    private String puntoSubida;
    private String puntoBajada;

    public Rutas(int idRutas, String puntoSubida, String puntoBajada) {
        this.idRutas = idRutas;
        this.puntoSubida = puntoSubida;
        this.puntoBajada = puntoBajada;
    }

    public void setIdRutas(int idRutas) {
        this.idRutas = idRutas;
    }
    public void setPuntoSubida(String puntoSubida) {
        this.puntoSubida = puntoSubida;
    }
    public void setPuntoBajada(String puntoBajada) {
        this.puntoBajada = puntoBajada;
    }

    public int getIdRutas() {
        return idRutas;
    }
    public String getPuntoSubida() {
        return puntoSubida;
    }
    public String getPuntoBajada() {
        return puntoBajada;
    }
}