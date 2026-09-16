package com.arlys.moviflexx.model.pojo;

public class Producto {
    private int id;
    private String nombre;
    private String descripcion;
    private int precio;
    private String imagen;
    private String categoria;
    private int disponible;
    private int restauranteId;

    public Producto() {}

    public Producto(int id, String nombre, int precio, String categoria, int restauranteId) {
        this.id = id;
        this.nombre = nombre;
        this.precio = precio;
        this.categoria = categoria;
        this.restauranteId = restauranteId;
        this.disponible = 1;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String n) { this.nombre = n; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String d) { this.descripcion = d; }
    public int getPrecio() { return precio; }
    public void setPrecio(int p) { this.precio = p; }
    public String getImagen() { return imagen; }
    public void setImagen(String i) { this.imagen = i; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String c) { this.categoria = c; }
    public int getDisponible() { return disponible; }
    public void setDisponible(int d) { this.disponible = d; }
    public int getRestauranteId() { return restauranteId; }
    public void setRestauranteId(int r) { this.restauranteId = r; }
}
