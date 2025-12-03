package com.arlys.moviflexx.model.pojo;

public class Pagos {
    private int idPagos;
    private int idMetodosPago; // FK
    private int idViajes; // FK
    private int idUsuarios; // FK
    private double monto; // Usamos double para DECIMAL(10,2)
    private String fechaPago; // Usamos String para DATETIME
    private String estado;

    public Pagos(int idPagos, int idMetodosPago, int idViajes, int idUsuarios, double monto, String fechaPago, String estado) {
        this.idPagos = idPagos;
        this.idMetodosPago = idMetodosPago;
        this.idViajes = idViajes;
        this.idUsuarios = idUsuarios;
        this.monto = monto;
        this.fechaPago = fechaPago;
        this.estado = estado;
    }

    public void setIdPagos(int idPagos) {
        this.idPagos = idPagos;
    }
    public void setIdMetodosPago(int idMetodosPago) {
        this.idMetodosPago = idMetodosPago;
    }
    public void setIdViajes(int idViajes) {
        this.idViajes = idViajes;
    }
    public void setIdUsuarios(int idUsuarios) {
        this.idUsuarios = idUsuarios;
    }
    public void setMonto(double monto) {
        this.monto = monto;
    }
    public void setFechaPago(String fechaPago) {
        this.fechaPago = fechaPago;
    }
    public void setEstado(String estado) {
        this.estado = estado;
    }

    public int getIdPagos() {
        return idPagos;
    }
    public int getIdMetodosPago() {
        return idMetodosPago;
    }
    public int getIdViajes() {
        return idViajes;
    }
    public int getIdUsuarios() {
        return idUsuarios;
    }
    public double getMonto() {
        return monto;
    }
    public String getFechaPago() {
        return fechaPago;
    }
    public String getEstado() {
        return estado;
    }
}