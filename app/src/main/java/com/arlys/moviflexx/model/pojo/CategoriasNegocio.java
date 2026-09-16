package com.arlys.moviflexx.model.pojo;

public class CategoriasNegocio {
    private int id;
    private String nombre;
    private String icono;
    private int activo;

    public CategoriasNegocio() {}

    public CategoriasNegocio(int id, String nombre, String icono) {
        this.id = id;
        this.nombre = nombre;
        this.icono = icono;
        this.activo = 1;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String n) { this.nombre = n; }
    public String getIcono() { return icono; }
    public void setIcono(String i) { this.icono = i; }
}
