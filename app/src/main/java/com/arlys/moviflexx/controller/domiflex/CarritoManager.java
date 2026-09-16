package com.arlys.moviflexx.controller.domiflex;

import com.arlys.moviflexx.model.pojo.Producto;

import java.util.ArrayList;
import java.util.List;

public class CarritoManager {
    private static CarritoManager instance;
    private List<Producto> items = new ArrayList<>();
    private List<Integer> cantidades = new ArrayList<>();

    public static CarritoManager getInstance(){
        if(instance==null) instance=new CarritoManager();
        return instance;
    }
    public void add(Producto p){
        int idx = -1;
        for(int i=0;i<items.size();i++) if(items.get(i).getId()==p.getId()) idx=i;
        if(idx>=0) cantidades.set(idx, cantidades.get(idx)+1);
        else { items.add(p); cantidades.add(1); }
    }
    public List<Producto> getItems(){ return items; }
    public List<Integer> getCantidades(){ return cantidades; }
    public int getTotal(){
        int t=0; for(int i=0;i<items.size();i++) t+= items.get(i).getPrecio()*cantidades.get(i);
        return t;
    }
    public void clear(){ items.clear(); cantidades.clear(); }
    public int size(){ return items.size(); }
}
