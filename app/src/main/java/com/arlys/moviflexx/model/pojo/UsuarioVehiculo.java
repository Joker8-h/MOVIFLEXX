package com.arlys.moviflexx.model.pojo;

// Tabla de unión entre Usuarios y Vehiculos
public class UsuarioVehiculo {
    private int idUsuarios; // FK
    private int idVehiculos; // FK

    public UsuarioVehiculo(int idUsuarios, int idVehiculos) {
        this.idUsuarios = idUsuarios;
        this.idVehiculos = idVehiculos;
    }

    public void setIdUsuarios(int idUsuarios) {
        this.idUsuarios = idUsuarios;
    }
    public void setIdVehiculos(int idVehiculos) {
        this.idVehiculos = idVehiculos;
    }

    public int getIdUsuarios() {
        return idUsuarios;
    }
    public int getIdVehiculos() {
        return idVehiculos;
    }
}