package com.arlys.moviflexx.model.pojo;

import java.util.Date;

public class RegisterDatos {

    private String nombre;
    private  int telefono;
    private  String correo;
    private String password;
    private String confirmacion;
    private String rol;


    public RegisterDatos(){

    }


    public RegisterDatos(String nombre, int telefono, String correo, String password, String confirmacion, String rol){
        this.nombre = nombre;
        this.telefono = telefono;
        this.correo = correo;
        this.password = password;
        this.confirmacion = confirmacion;
        this.rol = rol;

    }

    public void setNombre(String nombre){this.nombre = nombre;}
    public void setTelefono(int telefono){this.telefono = telefono;}
    public  void setCorreo (String correo){this.correo = correo;}
    public void  setPassword(String password){this.password = password;}
    public void setConfirmacion(String confirmacion){this.confirmacion = confirmacion;}
    public void setRol(String rol){this.rol = rol;}


    //_______________________________________________________

    public  String getNombre(){return nombre;}
    public  int getTelefono(){return telefono;}
    public String getCorreo(){return correo;}
    public String getPassword(){return password;}
    public String getConfirmacion(){return confirmacion;}
    public String getRol(){return rol;}



}
