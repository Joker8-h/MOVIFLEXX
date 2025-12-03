package com.arlys.moviflexx.model.pojo;

// Tabla de unión y datos adicionales para la relación Usuario-Viaje
public class UsuarioViaje {
    private int idUsuarios; // FK
    private int idViajes; // FK
    private int idTarifas; // FK
    private String rolEnViaje; // Usamos String para ENUM
    private double distanciaRecorrida; // Usamos double para DECIMAL(10,2)
    private double precioFinal; // Usamos double para DECIMAL(10,2)

    public UsuarioViaje(int idUsuarios, int idViajes, int idTarifas, String rolEnViaje, double distanciaRecorrida, double precioFinal) {
        this.idUsuarios = idUsuarios;
        this.idViajes = idViajes;
        this.idTarifas = idTarifas;
        this.rolEnViaje = rolEnViaje;
        this.distanciaRecorrida = distanciaRecorrida;
        this.precioFinal = precioFinal;
    }

    public void setIdUsuarios(int idUsuarios) {
        this.idUsuarios = idUsuarios;
    }
    public void setIdViajes(int idViajes) {
        this.idViajes = idViajes;
    }
    public void setIdTarifas(int idTarifas) {
        this.idTarifas = idTarifas;
    }
    public void setRolEnViaje(String rolEnViaje) {
        this.rolEnViaje = rolEnViaje;
    }
    public void setDistanciaRecorrida(double distanciaRecorrida) {
        this.distanciaRecorrida = distanciaRecorrida;
    }
    public void setPrecioFinal(double precioFinal) {
        this.precioFinal = precioFinal;
    }

    public int getIdUsuarios() {
        return idUsuarios;
    }
    public int getIdViajes() {
        return idViajes;
    }
    public int getIdTarifas() {
        return idTarifas;
    }
    public String getRolEnViaje() {
        return rolEnViaje;
    }
    public double getDistanciaRecorrida() {
        return distanciaRecorrida;
    }
    public double getPrecioFinal() {
        return precioFinal;
    }
}