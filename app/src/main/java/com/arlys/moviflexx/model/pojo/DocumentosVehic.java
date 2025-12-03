package com.arlys.moviflexx.model.pojo;

public class DocumentosVehic {
    private int idRegistro;
    private int idDocumentoCatalogo; // FK
    private int idVehiculos; // FK
    private String urlArchivo;
    private String fechaVencimiento; // Usamos String para DATE
    private String estado; // Usamos String para ENUM
    private String estadoFinal; // Usamos String para ENUM
    private String fechaSubida; // Usamos String para DATETIME

    public DocumentosVehic(int idRegistro, int idDocumentoCatalogo, int idVehiculos, String urlArchivo, String fechaVencimiento, String estado, String estadoFinal, String fechaSubida) {
        this.idRegistro = idRegistro;
        this.idDocumentoCatalogo = idDocumentoCatalogo;
        this.idVehiculos = idVehiculos;
        this.urlArchivo = urlArchivo;
        this.fechaVencimiento = fechaVencimiento;
        this.estado = estado;
        this.estadoFinal = estadoFinal;
        this.fechaSubida = fechaSubida;
    }

    public void setIdRegistro(int idRegistro) {
        this.idRegistro = idRegistro;
    }
    public void setIdDocumentoCatalogo(int idDocumentoCatalogo) {
        this.idDocumentoCatalogo = idDocumentoCatalogo;
    }
    public void setIdVehiculos(int idVehiculos) {
        this.idVehiculos = idVehiculos;
    }
    public void setUrlArchivo(String urlArchivo) {
        this.urlArchivo = urlArchivo;
    }
    public void setFechaVencimiento(String fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }
    public void setEstado(String estado) {
        this.estado = estado;
    }
    public void setEstadoFinal(String estadoFinal) {
        this.estadoFinal = estadoFinal;
    }
    public void setFechaSubida(String fechaSubida) {
        this.fechaSubida = fechaSubida;
    }

    public int getIdRegistro() {
        return idRegistro;
    }
    public int getIdDocumentoCatalogo() {
        return idDocumentoCatalogo;
    }
    public int getIdVehiculos() {
        return idVehiculos;
    }
    public String getUrlArchivo() {
        return urlArchivo;
    }
    public String getFechaVencimiento() {
        return fechaVencimiento;
    }
    public String getEstado() {
        return estado;
    }
    public String getEstadoFinal() {
        return estadoFinal;
    }
    public String getFechaSubida() {
        return fechaSubida;
    }
}