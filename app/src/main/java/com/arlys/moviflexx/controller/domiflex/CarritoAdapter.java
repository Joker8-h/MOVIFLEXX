package com.arlys.moviflexx.controller.domiflex;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.pojo.Producto;

import java.util.List;

public class CarritoAdapter extends RecyclerView.Adapter<CarritoAdapter.VH> {
    List<Producto> items; List<Integer> cants;
    public CarritoAdapter(List<Producto> items, List<Integer> cants){ this.items=items; this.cants=cants; }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p,int v){ return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_carrito, p, false));}
    @Override public void onBindViewHolder(@NonNull VH h,int pos){
        h.tvNombre.setText(items.get(pos).getNombre()+" x"+cants.get(pos));
        h.tvPrecio.setText("$"+(items.get(pos).getPrecio()*cants.get(pos)));
    }
    @Override public int getItemCount(){ return items.size();}
    static class VH extends RecyclerView.ViewHolder{ TextView tvNombre, tvPrecio; VH(View v){ super(v); tvNombre=v.findViewById(R.id.tvNombre); tvPrecio=v.findViewById(R.id.tvPrecio);}}
}
