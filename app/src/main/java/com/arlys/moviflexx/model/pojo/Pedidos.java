package com.arlys.moviflexx.model.pojo;

// DomiFlex Fase 2: reemplaza Viajes (cupos viajes) por Pedidos domicilio
// Estados: CREADO->ASIGNADO->RECOGIENDO->EN_CAMINO->ENTREGADO->CANCELADO
public class Pedidos {
    private int idPedido;
    private int idCliente;
    private Integer idRepartidor;
    private Integer idComercio;
    private Integer idRuta;
    private Integer idVehiculo;
    private Integer negocioId;

    private String nombreRecogida;
    private String dirRecogida;
    private double latRecogida;
    private double lngRecogida;

    private String nombreEntrega;
    private String dirEntrega;
    private double latEntrega;
    private double lngEntrega;

    private String detallePedido;
    private double distanciaKm;
    private double subtotal;
    private double comisionPlataforma;
    private double total;

    private String tipoPago; // EFECTIVO default
    private String estado; // CREADO default
    private String creadoEn;

    public Pedidos() {}

    public Pedidos(int idPedido, int idCliente, String nombreRecogida, String dirRecogida,
                   double latRecogida, double lngRecogida, String nombreEntrega, String dirEntrega,
                   double latEntrega, double lngEntrega, String estado, double total) {
        this.idPedido = idPedido;
        this.idCliente = idCliente;
        this.nombreRecogida = nombreRecogida;
        this.dirRecogida = dirRecogida;
        this.latRecogida = latRecogida;
        this.lngRecogida = lngRecogida;
        this.nombreEntrega = nombreEntrega;
        this.dirEntrega = dirEntrega;
        this.latEntrega = latEntrega;
        this.lngEntrega = lngEntrega;
        this.estado = estado;
        this.total = total;
        this.tipoPago = "EFECTIVO";
    }

    // Getters/Setters
    public int getIdPedido() { return idPedido; }
    public void setIdPedido(int idPedido) { this.idPedido = idPedido; }
    public int getIdCliente() { return idCliente; }
    public void setIdCliente(int idCliente) { this.idCliente = idCliente; }
    public Integer getIdRepartidor() { return idRepartidor; }
    public void setIdRepartidor(Integer idRepartidor) { this.idRepartidor = idRepartidor; }
    public Integer getNegocioId() { return negocioId; }
    public void setNegocioId(Integer negocioId) { this.negocioId = negocioId; }
    public String getNombreRecogida() { return nombreRecogida; }
    public void setNombreRecogida(String n) { this.nombreRecogida = n; }
    public String getDirRecogida() { return dirRecogida; }
    public void setDirRecogida(String d) { this.dirRecogida = d; }
    public double getLatRecogida() { return latRecogida; }
    public void setLatRecogida(double v) { this.latRecogida = v; }
    public double getLngRecogida() { return lngRecogida; }
    public void setLngRecogida(double v) { this.lngRecogida = v; }
    public String getNombreEntrega() { return nombreEntrega; }
    public void setNombreEntrega(String n) { this.nombreEntrega = n; }
    public String getDirEntrega() { return dirEntrega; }
    public void setDirEntrega(String d) { this.dirEntrega = d; }
    public double getLatEntrega() { return latEntrega; }
    public void setLatEntrega(double v) { this.latEntrega = v; }
    public double getLngEntrega() { return lngEntrega; }
    public void setLngEntrega(double v) { this.lngEntrega = v; }
    public String getDetallePedido() { return detallePedido; }
    public void setDetallePedido(String d) { this.detallePedido = d; }
    public double getDistanciaKm() { return distanciaKm; }
    public void setDistanciaKm(double v) { this.distanciaKm = v; }
    public double getTotal() { return total; }
    public void setTotal(double v) { this.total = v; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double v) { this.subtotal = v; }
    public double getComisionPlataforma() { return comisionPlataforma; }
    public void setComisionPlataforma(double v) { this.comisionPlataforma = v; }
    public String getTipoPago() { return tipoPago; }
    public void setTipoPago(String t) { this.tipoPago = t; }
    public String getEstado() { return estado; }
    public void setEstado(String e) { this.estado = e; }
    public String getCreadoEn() { return creadoEn; }
    public void setCreadoEn(String c) { this.creadoEn = c; }
}
