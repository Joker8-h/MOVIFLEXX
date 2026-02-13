package com.arlys.moviflexx.model;

public class Ruta {

    private int idRuta;
    private String nombre;
    private String descripcion;

    private String origen;
    private String destino;

    private double latOrigen;
    private double lngOrigen;

    private double latDestino;
    private double lngDestino;

    private double distancia;
    private double duracion;

    private String tipoTransporte;
    private String estado;

    // Constructor vacío (IMPORTANTE)
    public Ruta() {}

    // Constructor completo
    public Ruta(int idRuta, String nombre, String descripcion,
                String origen, String destino,
                double latOrigen, double lngOrigen,
                double latDestino, double lngDestino,
                double distancia, double duracion,
                String tipoTransporte, String estado) {

        this.idRuta = idRuta;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.origen = origen;
        this.destino = destino;
        this.latOrigen = latOrigen;
        this.lngOrigen = lngOrigen;
        this.latDestino = latDestino;
        this.lngDestino = lngDestino;
        this.distancia = distancia;
        this.duracion = duracion;
        this.tipoTransporte = tipoTransporte;
        this.estado = estado;
    }

    // GETTERS y SETTERS

    public int getIdRuta() {
        return idRuta;
    }

    public void setIdRuta(int idRuta) {
        this.idRuta = idRuta;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public double getLatOrigen() {
        return latOrigen;
    }

    public void setLatOrigen(double latOrigen) {
        this.latOrigen = latOrigen;
    }

    public double getLngOrigen() {
        return lngOrigen;
    }

    public void setLngOrigen(double lngOrigen) {
        this.lngOrigen = lngOrigen;
    }

    public double getLatDestino() {
        return latDestino;
    }

    public void setLatDestino(double latDestino) {
        this.latDestino = latDestino;
    }

    public double getLngDestino() {
        return lngDestino;
    }

    public void setLngDestino(double lngDestino) {
        this.lngDestino = lngDestino;
    }

    public double getDistancia() {
        return distancia;
    }

    public void setDistancia(double distancia) {
        this.distancia = distancia;
    }

    public double getDuracion() {
        return duracion;
    }

    public void setDuracion(double duracion) {
        this.duracion = duracion;
    }

    public String getTipoTransporte() {
        return tipoTransporte;
    }

    public void setTipoTransporte(String tipoTransporte) {
        this.tipoTransporte = tipoTransporte;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
