package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Producto;

import java.util.ArrayList;
import java.util.List;

public class ProductoManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;
    public ProductoManager(Context context) { conexionBd = new ConexionBd(context); }
    public void openWr() { db = conexionBd.getWritableDatabase(); }
    public void openRd() { db = conexionBd.getReadableDatabase(); }
    public void close() { if (db != null && db.isOpen()) db.close(); }

    public long insert(Producto p) {
        openWr();
        ContentValues v = new ContentValues();
        v.put("nombre", p.getNombre());
        v.put("descripcion", p.getDescripcion());
        v.put("precio", p.getPrecio());
        v.put("imagen", p.getImagen());
        v.put("categoria", p.getCategoria());
        v.put("disponible", p.getDisponible());
        v.put("restauranteId", p.getRestauranteId());
        long id = db.insert("producto", null, v);
        close();
        return id;
    }

    public List<Producto> getByNegocio(int negocioId) {
        openRd();
        List<Producto> list = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM producto WHERE restauranteId=? AND disponible=1 ORDER BY categoria ASC", new String[]{String.valueOf(negocioId)});
        while (c.moveToNext()) {
            Producto p = new Producto();
            p.setId(c.getInt(c.getColumnIndexOrThrow("id")));
            p.setNombre(c.getString(c.getColumnIndexOrThrow("nombre")));
            p.setPrecio(c.getInt(c.getColumnIndexOrThrow("precio")));
            p.setCategoria(c.getString(c.getColumnIndexOrThrow("categoria")));
            p.setImagen(c.getString(c.getColumnIndexOrThrow("imagen")));
            p.setRestauranteId(c.getInt(c.getColumnIndexOrThrow("restauranteId")));
            list.add(p);
        }
        c.close(); close();
        return list;
    }
}
