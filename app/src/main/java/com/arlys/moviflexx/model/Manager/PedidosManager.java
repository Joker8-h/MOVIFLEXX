package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Pedidos;

import java.util.ArrayList;
import java.util.List;

public class PedidosManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public PedidosManager(Context context) { conexionBd = new ConexionBd(context); }

    public void openWr() { db = conexionBd.getWritableDatabase(); }
    public void openRd() { db = conexionBd.getReadableDatabase(); }
    public void close() { if (db != null && db.isOpen()) db.close(); }

    public long insert(Pedidos p) {
        openWr();
        ContentValues v = new ContentValues();
        v.put("idCliente", p.getIdCliente());
        if (p.getIdRepartidor() != null) v.put("idRepartidor", p.getIdRepartidor());
        if (p.getNegocioId() != null) v.put("negocioId", p.getNegocioId());
        v.put("nombreRecogida", p.getNombreRecogida());
        v.put("dirRecogida", p.getDirRecogida());
        v.put("latRecogida", p.getLatRecogida());
        v.put("lngRecogida", p.getLngRecogida());
        v.put("nombreEntrega", p.getNombreEntrega());
        v.put("dirEntrega", p.getDirEntrega());
        v.put("latEntrega", p.getLatEntrega());
        v.put("lngEntrega", p.getLngEntrega());
        v.put("detallePedido", p.getDetallePedido());
        v.put("distanciaKm", p.getDistanciaKm());
        v.put("subtotal", p.getSubtotal());
        v.put("comisionPlataforma", p.getComisionPlataforma());
        v.put("total", p.getTotal());
        v.put("tipoPago", p.getTipoPago() != null ? p.getTipoPago() : "EFECTIVO");
        v.put("estado", p.getEstado() != null ? p.getEstado() : "CREADO");
        long id = db.insert("pedidos", null, v);
        close();
        return id;
    }

    public List<Pedidos> getByCliente(int idCliente) {
        openRd();
        List<Pedidos> list = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM pedidos WHERE idCliente=? ORDER BY idPedido DESC", new String[]{String.valueOf(idCliente)});
        while (c.moveToNext()) {
            Pedidos p = new Pedidos();
            p.setIdPedido(c.getInt(c.getColumnIndexOrThrow("idPedido")));
            p.setIdCliente(c.getInt(c.getColumnIndexOrThrow("idCliente")));
            p.setEstado(c.getString(c.getColumnIndexOrThrow("estado")));
            p.setTotal(c.getDouble(c.getColumnIndexOrThrow("total")));
            p.setNombreRecogida(c.getString(c.getColumnIndexOrThrow("nombreRecogida")));
            p.setNombreEntrega(c.getString(c.getColumnIndexOrThrow("nombreEntrega")));
            list.add(p);
        }
        c.close(); close();
        return list;
    }

    public int updateEstado(int idPedido, String nuevoEstado) {
        openWr();
        ContentValues v = new ContentValues();
        v.put("estado", nuevoEstado);
        int rows = db.update("pedidos", v, "idPedido=?", new String[]{String.valueOf(idPedido)});
        close();
        return rows;
    }
}
