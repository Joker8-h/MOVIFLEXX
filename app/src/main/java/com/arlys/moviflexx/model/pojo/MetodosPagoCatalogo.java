package com.arlys.moviflexx.model.pojo;

public class MetodosPagoCatalogo {
    private int idMetodosPago;
    private String nombre;
    private String tipo;

    public MetodosPagoCatalogo(int idMetodosPago, String nombre, String tipo) {
        this.idMetodosPago = idMetodosPago;
        this.nombre = nombre;
        this.tipo = tipo;
    }

    public void setIdMetodosPago(int idMetodosPago) {
        this.idMetodosPago = idMetodosPago;
    }
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public int getIdMetodosPago() {
        return idMetodosPago;
    }
    public String getNombre() {
        return nombre;
    }
    public String getTipo() {
        return tipo;
    }
}