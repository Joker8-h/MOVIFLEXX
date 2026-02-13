package com.arlys.moviflexx.model;

import java.util.Date;

public class Reserva {

    private Long id;
    private Viaje viaje;
    private String pasajero;
    private Integer cantidadCupos;
    private Date fechaReserva;
    private String estado;

    public Reserva() {
    }

    public Reserva(Long id, Viaje viaje, String pasajero,
                   Integer cantidadCupos, Date fechaReserva,
                   String estado) {
        this.id = id;
        this.viaje = viaje;
        this.pasajero = pasajero;
        this.cantidadCupos = cantidadCupos;
        this.fechaReserva = fechaReserva;
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Viaje getViaje() {
        return viaje;
    }

    public void setViaje(Viaje viaje) {
        this.viaje = viaje;
    }

    public String getPasajero() {
        return pasajero;
    }

    public void setPasajero(String pasajero) {
        this.pasajero = pasajero;
    }

    public Integer getCantidadCupos() {
        return cantidadCupos;
    }

    public void setCantidadCupos(Integer cantidadCupos) {
        this.cantidadCupos = cantidadCupos;
    }

    public Date getFechaReserva() {
        return fechaReserva;
    }

    public void setFechaReserva(Date fechaReserva) {
        this.fechaReserva = fechaReserva;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
