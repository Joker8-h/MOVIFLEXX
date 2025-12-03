package com.arlys.moviflexx.model.pojo;

public class Tarifas {
    private int idTarifas;
    private double costoBase; // Usamos double para DECIMAL(10,2)
    private double costoPorKm; // Usamos double para DECIMAL(10,2)
    private String fechaVigencia; // Usamos String para DATE

    public Tarifas(int idTarifas, double costoBase, double costoPorKm, String fechaVigencia) {
        this.idTarifas = idTarifas;
        this.costoBase = costoBase;
        this.costoPorKm = costoPorKm;
        this.fechaVigencia = fechaVigencia;
    }

    public void setIdTarifas(int idTarifas) {
        this.idTarifas = idTarifas;
    }
    public void setCostoBase(double costoBase) {
        this.costoBase = costoBase;
    }
    public void setCostoPorKm(double costoPorKm) {
        this.costoPorKm = costoPorKm;
    }
    public void setFechaVigencia(String fechaVigencia) {
        this.fechaVigencia = fechaVigencia;
    }

    public int getIdTarifas() {
        return idTarifas;
    }
    public double getCostoBase() {
        return costoBase;
    }
    public double getCostoPorKm() {
        return costoPorKm;
    }
    public String getFechaVigencia() {
        return fechaVigencia;
    }
}