package com.arlys.moviflexx.model.pojo;

public class Roles {
    private int idRol;
    private String nombreRol;

    public Roles(int idRol, String nombreRol) {
        this.idRol = idRol;
        this.nombreRol = nombreRol;
    }

    public void setIdRol(int idRol) {
        this.idRol = idRol;
    }
    public void setNombreRol(String nombreRol) {
        this.nombreRol = nombreRol;
    }

    public int getIdRol() {
        return idRol;
    }
    public String getNombreRol() {
        return nombreRol;
    }
}