package com.arlys.moviflexx.model.pojo;

public class Usuarios {
    private int idUsuarios;
    private String nombre;
    private String apellido;
    private String correo;
    private String telefono;
    private String contrasena;

    public Usuarios(int idUsuarios, String nombre, String apellido, String correo, String telefono, String contrasena) {
        this.idUsuarios = idUsuarios;
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.telefono = telefono;
        this.contrasena = contrasena;
    }

    public void setIdUsuarios(int idUsuarios) {
        this.idUsuarios = idUsuarios;
    }
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    public void setApellido(String apellido) {
        this.apellido = apellido;
    }
    public void setCorreo(String correo) {
        this.correo = correo;
    }
    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }
    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public int getIdUsuarios() {
        return idUsuarios;
    }
    public String getNombre() {
        return nombre;
    }
    public String getApellido() {
        return apellido;
    }
    public String getCorreo() {
        return correo;
    }
    public String getTelefono() {
        return telefono;
    }
    public String getContrasena() {
        return contrasena;
    }
}