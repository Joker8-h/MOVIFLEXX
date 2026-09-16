package com.arlys.moviflexx.model.pojo;

// DomiFlex Fase 3: catálogo de negocios (restaurantes/tiendas)
public class Negocios {
    private int id;
    private String nombre;
    private String descripcion;
    private String tipo; // COMIDA, FARMACIA, SUPERMERCADO, etc
    private String direccion;
    private double latitud;
    private double longitud;
    private String telefono;
    private String imagen;
    private String banner;
    private double calificacion;
    private int totalCalificaciones;
    private int tiempoEstimadoMin;
    private int costoEnvio;
    private int envioMinimo;
    private int activo;
    private int ownerId;
    private Integer categoriaId;

    public Negocios() {}

    public Negocios(int id, String nombre, String tipo, String direccion, double latitud, double longitud) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.direccion = direccion;
        this.latitud = latitud;
        this.longitud = longitud;
        this.calificacion = 0;
        this.costoEnvio = 2000;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String n) { this.nombre = n; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String d) { this.descripcion = d; }
    public String getTipo() { return tipo; }
    public void setTipo(String t) { this.tipo = t; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String d) { this.direccion = d; }
    public double getLatitud() { return latitud; }
    public void setLatitud(double v) { this.latitud = v; }
    public double getLongitud() { return longitud; }
    public void setLongitud(double v) { this.longitud = v; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String t) { this.telefono = t; }
    public String getImagen() { return imagen; }
    public void setImagen(String i) { this.imagen = i; }
    public String getBanner() { return banner; }
    public void setBanner(String b) { this.banner = b; }
    public double getCalificacion() { return calificacion; }
    public void setCalificacion(double c) { this.calificacion = c; }
    public int getCostoEnvio() { return costoEnvio; }
    public void setCostoEnvio(int c) { this.costoEnvio = c; }
    public int getTiempoEstimadoMin() { return tiempoEstimadoMin; }
    public void setTiempoEstimadoMin(int t) { this.tiempoEstimadoMin = t; }
    public Integer getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Integer c) { this.categoriaId = c; }
    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int o) { this.ownerId = o; }
}
