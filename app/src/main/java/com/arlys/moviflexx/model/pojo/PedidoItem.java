package com.arlys.moviflexx.model.pojo;

public class PedidoItem {
    private int id;
    private int cantidad;
    private int precio;
    private int pedidoId;
    private int menuItemId;
    // Join con Producto para visual
    private Producto menuItem;

    public PedidoItem() {}

    public PedidoItem(int cantidad, int precio, int pedidoId, int menuItemId) {
        this.cantidad = cantidad;
        this.precio = precio;
        this.pedidoId = pedidoId;
        this.menuItemId = menuItemId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int c) { this.cantidad = c; }
    public int getPrecio() { return precio; }
    public void setPrecio(int p) { this.precio = p; }
    public int getPedidoId() { return pedidoId; }
    public void setPedidoId(int p) { this.pedidoId = p; }
    public int getMenuItemId() { return menuItemId; }
    public void setMenuItemId(int m) { this.menuItemId = m; }
    public Producto getMenuItem() { return menuItem; }
    public void setMenuItem(Producto m) { this.menuItem = m; }
}
