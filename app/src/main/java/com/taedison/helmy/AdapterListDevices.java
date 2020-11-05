package com.taedison.helmy;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/***
 * Class used for listing the Helmy devices.
 * It follows the standard implementation of recyclerviews, except that it can highlight views
 * according to the position occupied within the list
 */
public class AdapterListDevices extends RecyclerView.Adapter<AdapterListDevices.ViewHolder> {
    private ArrayList<String> mDataset;
    private Context context;
    private View.OnClickListener onClickListener;
    private int pos = -1; // position of the views that will change color

    AdapterListDevices(ArrayList<String> myDataset, Context context) {
        this.context = context;
        mDataset = myDataset;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.template_recycler_alert, parent, false);
        view.setOnClickListener(onClickListener);
        return new ViewHolder(view);
    }
    class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtNombre;
        boolean highlight;

        ViewHolder(View itemView) {
            super(itemView);
            txtNombre = itemView.findViewById(R.id.tvRecyclerAlert);
            highlight = false;
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // automatically executed when there is a change in the data. It loops through all visible views listed in the recycler
        holder.txtNombre.setText(mDataset.get(position));

        holder.itemView.setOnClickListener(onClickListener); // add the same listener to all views in the recyclerView

        if(position == pos){
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.background_buttons));
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE);
        }
    }

    public void setOnItemClickListener(View.OnClickListener onClick) {
        onClickListener = onClick;
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public void HighlightView(int p){
        pos = p;
    }

}
