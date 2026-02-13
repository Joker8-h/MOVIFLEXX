package com.arlys.moviflexx.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Viaje {

    private int idRuta;
    private int idVehiculo;
    private String fechaSalida;
    private int cuposTotales;
    private int cuposDisponibles;
    private double precio;
    private String estado;

    // ================= GETTERS =================

    public int getIdRuta() {
        return idRuta;
    }

    public int getIdVehiculo() {
        return idVehiculo;
    }

    public String getFechaSalida() {
        return fechaSalida;
    }

    public int getCuposTotales() {
        return cuposTotales;
    }

    public int getCuposDisponibles() {
        return cuposDisponibles;
    }

    public double getPrecio() {
        return precio;
    }

    public String getEstado() {
        return estado;
    }

    // ================= SETTERS =================

    public void setIdRuta(int idRuta) {
        this.idRuta = idRuta;
    }

    public void setIdVehiculo(int idVehiculo) {
        this.idVehiculo = idVehiculo;
    }

    public void setFechaSalida(String fechaSalida) {
        this.fechaSalida = fechaSalida;
    }

    public void setCuposTotales(int cuposTotales) {
        this.cuposTotales = cuposTotales;
    }

    public void setCuposDisponibles(int cuposDisponibles) {
        this.cuposDisponibles = cuposDisponibles;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    // ================= JSON =================

    public JSONObject toJson() {

        JSONObject json = new JSONObject();

        try {

            json.put("idRuta", idRuta);
            json.put("idVehiculo", idVehiculo);
            json.put("fechaHoraSalida", fechaSalida);
            json.put("cuposTotales", cuposTotales);
            json.put("cuposDisponibles", cuposDisponibles);
            json.put("precio", precio);
            json.put("estado", estado);

        }
        catch (JSONException e) {
            e.printStackTrace();
        }

        return json;
    }

}
