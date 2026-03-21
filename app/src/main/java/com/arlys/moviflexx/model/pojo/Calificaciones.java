package com.arlys.moviflexx.model.pojo;

public class Calificaciones {
    private int    idCalificacion;
    private int    idViaje;
    private int    idCalificador;
    private int    idCalificado;
    private int    puntuacion;
    private String comentario;    // opcional — puede ser null o vacío
    private String creadoEn;

    // Campo transitorio: no viene del backend, se asigna en UI
    private String nombreCalificado;

    public Calificaciones() {}

    public Calificaciones(int idCalificacion, int idViaje, int idCalificador,
                          int idCalificado, int puntuacion,
                          String comentario, String creadoEn) {
        this.idCalificacion = idCalificacion;
        this.idViaje        = idViaje;
        this.idCalificador  = idCalificador;
        this.idCalificado   = idCalificado;
        this.puntuacion     = puntuacion;
        this.comentario     = comentario;
        this.creadoEn       = creadoEn;
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public int    getIdCalificacion()   { return idCalificacion; }
    public int    getIdViaje()          { return idViaje; }
    public int    getIdCalificador()    { return idCalificador; }
    public int    getIdCalificado()     { return idCalificado; }
    public int    getPuntuacion()       { return puntuacion; }
    public String getComentario()       { return comentario; }
    public String getCreadoEn()         { return creadoEn; }
    public String getNombreCalificado() { return nombreCalificado; }

    // ── Setters ───────────────────────────────────────────────────────────
    public void setIdCalificacion(int v)    { this.idCalificacion = v; }
    public void setIdViaje(int v)           { this.idViaje = v; }
    public void setIdCalificador(int v)     { this.idCalificador = v; }
    public void setIdCalificado(int v)      { this.idCalificado = v; }
    public void setPuntuacion(int v)        { this.puntuacion = v; }
    public void setComentario(String v)     { this.comentario = v; }
    public void setCreadoEn(String v)       { this.creadoEn = v; }
    public void setNombreCalificado(String v){ this.nombreCalificado = v; }

    // ── Helper estrellas ──────────────────────────────────────────────────
    public static String construirEstrellas(int p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < p ? "⭐" : "☆");
        return sb.toString();
    }
}