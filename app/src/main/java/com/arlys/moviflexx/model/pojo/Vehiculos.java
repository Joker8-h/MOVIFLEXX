package com.arlys.moviflexx.model.pojo;

public class Vehiculos {
    private int idVehiculos;
    private String tipoVehicular;
    private String placa;
    private String modelo;
    private String color;

    public Vehiculos(int idVehiculos, String tipoVehicular, String placa, String modelo, String color) {
        this.idVehiculos = idVehiculos;
        this.tipoVehicular = tipoVehicular;
        this.placa = placa;
        this.modelo = modelo;
        this.color = color;
    }

    public void setIdVehiculos(int idVehiculos) {
        this.idVehiculos = idVehiculos;
    }
    public void setTipoVehicular(String tipoVehicular) {
        this.tipoVehicular = tipoVehicular;
    }
    public void setPlaca(String placa) {
        this.placa = placa;
    }
    public void setModelo(String modelo) {
        this.modelo = modelo;
    }
    public void setColor(String color) {
        this.color = color;
    }

    public int getIdVehiculos() {
        return idVehiculos;
    }
    public String getTipoVehicular() {
        return tipoVehicular;
    }
    public String getPlaca() {
        return placa;
    }
    public String getModelo() {
        return modelo;
    }
    public String getColor() {
        return color;
    }
}