package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.RegisterDatos;

public class RegisterManager {

    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public RegisterManager(Context context){


        conexionBd = new ConexionBd(context);
    }

    public void openBdWr(){
        db=conexionBd.getWritableDatabase();
    }
    public void openBdRd(){
        db=conexionBd.getReadableDatabase();
    }

    public void closeBd(){
        db.close();
    }

    public long insertData(RegisterDatos registerDatos){
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("NOMBRE", registerDatos.getNombre());
        values.put("TELEFONO", registerDatos.getTelefono());
        values.put("CORREO", registerDatos.getCorreo());
        values.put("PASSWORD", registerDatos.getPassword());
        values.put("CONFIRMACION", registerDatos.getConfirmacion());
        values.put("ROL", registerDatos.getRol());
        values.put("TERMINOS", registerDatos.getTerminos() );
        long id = db.insert("RegisterDatos",null,values);
        closeBd();
        return id;

    }

}
