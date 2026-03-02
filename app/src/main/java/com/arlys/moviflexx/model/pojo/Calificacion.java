package com.arlys.moviflexx.model.pojo;

import org.json.JSONObject;

/**
 * POJO - Calificacion
 * Mapea la respuesta JSON de la API de calificaciones.
 */
public class Calificacion {

    private int     idCalificacion;
    private int     idViaje;
    private int     idCalificador;
    private int     idCalificado;
    private int     puntuacion;
    private String  estrellas;
    private String  etiqueta;
    private String  comentario;
    private String  creadoEn;
    private boolean yaCalifico;

    public Calificacion() {}

    public static Calificacion fromJson(JSONObject json) {
        if (json == null) return null;
        Calificacion c = new Calificacion();
        c.idCalificacion = json.optInt("idCalificacion", 0);
        c.idViaje        = json.optInt("idViaje",        0);
        c.idCalificador  = json.optInt("idCalificador",  0);
        c.idCalificado   = json.optInt("idCalificado",   0);
        c.puntuacion     = json.optInt("puntuacion",     0);
        c.estrellas      = json.optString("estrellas",   "");
        c.etiqueta       = json.optString("etiqueta",    "");
        c.comentario     = json.optString("comentario",  "");
        c.creadoEn       = json.optString("creadoEn",    "");
        c.yaCalifico     = json.optBoolean("yaCalifico", false);
        return c;
    }

    public static String construirEstrellas(int puntuacion) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(Math.max(puntuacion, 0), 5); i++) sb.append("\u2B50");
        return sb.toString();
    }

    public static String etiquetaEstrellas(int puntuacion) {
        switch (puntuacion) {
            case 5: return "Excelente";
            case 4: return "Muy bueno";
            case 3: return "Bueno";
            case 2: return "Regular";
            case 1: return "Muy malo";
            default: return "Sin calificacion";
        }
    }

    // Getters
    public int     getIdCalificacion() { return idCalificacion; }
    public int     getIdViaje()        { return idViaje; }
    public int     getIdCalificador()  { return idCalificador; }
    public int     getIdCalificado()   { return idCalificado; }
    public int     getPuntuacion()     { return puntuacion; }
    public String  getEstrellas()      { return estrellas; }
    public String  getEtiqueta()       { return etiqueta; }
    public String  getComentario()     { return comentario; }
    public String  getCreadoEn()       { return creadoEn; }
    public boolean isYaCalifico()      { return yaCalifico; }

    // Setters
    public void setIdCalificacion(int v)  { this.idCalificacion = v; }
    public void setIdViaje(int v)         { this.idViaje = v; }
    public void setIdCalificador(int v)   { this.idCalificador = v; }
    public void setIdCalificado(int v)    { this.idCalificado = v; }
    public void setPuntuacion(int v)      { this.puntuacion = v; }
    public void setEstrellas(String v)    { this.estrellas = v; }
    public void setEtiqueta(String v)     { this.etiqueta = v; }
    public void setComentario(String v)   { this.comentario = v; }
    public void setCreadoEn(String v)     { this.creadoEn = v; }
    public void setYaCalifico(boolean v)  { this.yaCalifico = v; }
}