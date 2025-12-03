package com.arlys.moviflexx.model.pojo;

// Tabla de unión entre Usuarios y Roles
public class UsuarioRol {
    private int idUsuarios; // FK
    private int idRol; // FK

    public UsuarioRol(int idUsuarios, int idRol) {
        this.idUsuarios = idUsuarios;
        this.idRol = idRol;
    }

    public void setIdUsuarios(int idUsuarios) {
        this.idUsuarios = idUsuarios;
    }
    public void setIdRol(int idRol) {
        this.idRol = idRol;
    }

    public int getIdUsuarios() {
        return idUsuarios;
    }
    public int getIdRol() {
        return idRol;
    }
}