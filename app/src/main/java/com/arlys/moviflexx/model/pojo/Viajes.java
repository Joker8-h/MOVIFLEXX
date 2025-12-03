package com.arlys.moviflexx.model.pojo;

public class Viajes {
    private int idViajes;
    private int idVehiculos; // FK
    private String origen;
    private String destino;
    private String fechaHoraSalida; // Usamos String para DATETIME
    private int cuposTotales;
    private int cuposDisponibles;
    private String estado;

    public Viajes(int idViajes, int idVehiculos, String origen, String destino, String fechaHoraSalida, int cuposTotales, int cuposDisponibles, String estado) {
        this.idViajes = idViajes;
        this.idVehiculos = idVehiculos;
        this.origen = origen;
        this.destino = destino;
        this.fechaHoraSalida = fechaHoraSalida;
        this.cuposTotales = cuposTotales;
        this.cuposDisponibles = cuposDisponibles;
        this.estado = estado;
    }

    public void setIdViajes(int idViajes) {
        this.idViajes = idViajes;
    }
    public void setIdVehiculos(int idVehiculos) {
        this.idVehiculos = idVehiculos;
    }
    public void setOrigen(String origen) {
        this.origen = origen;
    }
    public void setDestino(String destino) {
        this.destino = destino;
    }
    public void setFechaHoraSalida(String fechaHoraSalida) {
        this.fechaHoraSalida = fechaHoraSalida;
    }
    public void setCuposTotales(int cuposTotales) {
        this.cuposTotales = cuposTotales;
    }
    public void setCuposDisponibles(int cuposDisponibles) {
        this.cuposDisponibles = cuposDisponibles;
    }
    public void setEstado(String estado) {
        this.estado = estado;
    }

    public int getIdViajes() {
        return idViajes;
    }
    public int getIdVehiculos() {
        return idVehiculos;
    }
    public String getOrigen() {
        return origen;
    }
    public String getDestino() {
        return destino;
    }
    public String getFechaHoraSalida() {
        return fechaHoraSalida;
    }
    public int getCuposTotales() {
        return cuposTotales;
    }
    public int getCuposDisponibles() {
        return cuposDisponibles;
    }
    public String getEstado() {
        return estado;
    }
}