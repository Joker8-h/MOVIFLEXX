package com.arlys.moviflexx.model.pojo;

public class Calificaciones {
    private int idCalificacion;
    private int idUsuarios; // FK
    private int idViajes; // FK
    private int puntuacion;
    private String comentario;
    private String fechaCalificacion; // Usamos String para DATETIME

    public Calificaciones(int idCalificacion, int idUsuarios, int idViajes, int puntuacion, String comentario, String fechaCalificacion) {
        this.idCalificacion = idCalificacion;
        this.idUsuarios = idUsuarios;
        this.idViajes = idViajes;
        this.puntuacion = puntuacion;
        this.comentario = comentario;
        this.fechaCalificacion = fechaCalificacion;
    }

    public void setIdCalificacion(int idCalificacion) {
        this.idCalificacion = idCalificacion;
    }
    public void setIdUsuarios(int idUsuarios) {
        this.idUsuarios = idUsuarios;
    }
    public void setIdViajes(int idViajes) {
        this.idViajes = idViajes;
    }
    public void setPuntuacion(int puntuacion) {
        this.puntuacion = puntuacion;
    }
    public void setComentario(String comentario) {
        this.comentario = comentario;
    }
    public void setFechaCalificacion(String fechaCalificacion) {
        this.fechaCalificacion = fechaCalificacion;
    }

    public int getIdCalificacion() {
        return idCalificacion;
    }
    public int getIdUsuarios() {
        return idUsuarios;
    }
    public int getIdViajes() {
        return idViajes;
    }
    public int getPuntuacion() {
        return puntuacion;
    }
    public String getComentario() {
        return comentario;
    }
    public String getFechaCalificacion() {
        return fechaCalificacion;
    }
}