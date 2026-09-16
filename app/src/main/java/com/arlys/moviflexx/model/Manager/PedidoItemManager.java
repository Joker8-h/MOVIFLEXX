package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.PedidoItem;

public class PedidoItemManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;
    public PedidoItemManager(Context context) { conexionBd = new ConexionBd(context); }
    public void openWr() { db = conexionBd.getWritableDatabase(); }
    public void close() { if (db != null && db.isOpen()) db.close(); }

    public long insert(PedidoItem item) {
        openWr();
        ContentValues v = new ContentValues();
        v.put("cantidad", item.getCantidad());
        v.put("precio", item.getPrecio());
        v.put("pedidoId", item.getPedidoId());
        v.put("menuItemId", item.getMenuItemId());
        long id = db.insert("pedido_item", null, v);
        close();
        return id;
    }
}
