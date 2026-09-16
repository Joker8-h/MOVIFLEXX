package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Negocios;

import java.util.ArrayList;
import java.util.List;

public class NegociosManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;
    public NegociosManager(Context context) { conexionBd = new ConexionBd(context); }
    public void openWr() { db = conexionBd.getWritableDatabase(); }
    public void openRd() { db = conexionBd.getReadableDatabase(); }
    public void close() { if (db != null && db.isOpen()) db.close(); }

    public long insert(Negocios n) {
        openWr();
        ContentValues v = new ContentValues();
        v.put("nombre", n.getNombre());
        v.put("descripcion", n.getDescripcion());
        v.put("tipo", n.getTipo());
        v.put("direccion", n.getDireccion());
        v.put("latitud", n.getLatitud());
        v.put("longitud", n.getLongitud());
        v.put("telefono", n.getTelefono());
        v.put("imagen", n.getImagen());
        v.put("calificacion", n.getCalificacion());
        v.put("costoEnvio", n.getCostoEnvio());
        v.put("categoriaId", n.getCategoriaId());
        v.put("ownerId", n.getOwnerId());
        long id = db.insert("negocios", null, v);
        close();
        return id;
    }

    public List<Negocios> getAll(String tipo) {
        openRd();
        List<Negocios> list = new ArrayList<>();
        String sql = "SELECT * FROM negocios WHERE activo=1";
        String[] args = null;
        if (tipo != null) { sql += " AND tipo=?"; args = new String[]{tipo}; }
        sql += " ORDER BY calificacion DESC";
        Cursor c = args == null ? db.rawQuery(sql, null) : db.rawQuery(sql, args);
        while (c.moveToNext()) {
            Negocios n = new Negocios();
            n.setId(c.getInt(c.getColumnIndexOrThrow("id")));
            n.setNombre(c.getString(c.getColumnIndexOrThrow("nombre")));
            n.setTipo(c.getString(c.getColumnIndexOrThrow("tipo")));
            n.setDireccion(c.getString(c.getColumnIndexOrThrow("direccion")));
            n.setCalificacion(c.getDouble(c.getColumnIndexOrThrow("calificacion")));
            n.setImagen(c.getString(c.getColumnIndexOrThrow("imagen")));
            list.add(n);
        }
        c.close(); close();
        return list;
    }
}
