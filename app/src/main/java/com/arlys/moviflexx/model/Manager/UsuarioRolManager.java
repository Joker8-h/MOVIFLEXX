package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.UsuarioRol;

public class UsuarioRolManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public UsuarioRolManager(Context context) {
        conexionBd = new ConexionBd(context);
    }

    public void openBdWr() {
        db = conexionBd.getWritableDatabase();
    }

    public void openBdRd() {
        db = conexionBd.getReadableDatabase();
    };

    public void closeBd() {
        db.close();
    }

    /**
     * Inserta un nuevo registro en la tabla usuario_rol.
     */
    public long insertData(UsuarioRol usuarioRol) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("idUsuarios", usuarioRol.getIdUsuarios());
        values.put("idRol", usuarioRol.getIdRol());

        long id = db.insert("usuario_rol", null, values);
        closeBd();
        return id;
    }
}