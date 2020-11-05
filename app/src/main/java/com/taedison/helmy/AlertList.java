package com.taedison.helmy;

import android.app.Dialog;
import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/***
 * Class used for displaying lists in a dislog that uses a customized layout.
 * It is used for listing bluetooth devices and stored helmy devices
 */
public class AlertList {

    private Dialog dialog;
    private TextView tvMessage, btnCancel;
    private RecyclerView recyclerView;
    private TextView btnPositive, btnNegative, btnNeutral;
    private Context ctx;

    AlertList(Context context){
        ctx = context;
        dialog = new Dialog(context);
        dialog.setContentView(R.layout.alert_list); // customized layout

        btnCancel = dialog.findViewById(R.id.btnCancelAlert);
        tvMessage = dialog.findViewById(R.id.tvAlertMessage);
        recyclerView = dialog.findViewById(R.id.recyclerAlert);
        btnPositive = dialog.findViewById(R.id.btnPositive);
        btnNegative = dialog.findViewById(R.id.btnNegative);
        btnNeutral = dialog.findViewById(R.id.btnNeutral);

        tvMessage.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        btnPositive.setVisibility(View.GONE);
        btnNegative.setVisibility(View.GONE);
        btnNeutral.setVisibility(View.GONE);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
    }

    public void hideCancelButton(){
        btnCancel.setVisibility(View.INVISIBLE);
    }

    public void setDialogMessage(String message){
        tvMessage.setVisibility(View.VISIBLE);
        tvMessage.setText(message);
    }

    public void setDialogMessage(int message){
        // this method is so that the hyperlinks work
        tvMessage.setVisibility(View.VISIBLE);
        tvMessage.setText(message);
    }

    public void setRecyclerView(AdapterListDevices adapter){
        recyclerView.setVisibility(View.VISIBLE);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        recyclerView.setAdapter(adapter);
    }

    public RecyclerView getRecyclerView(){
        return recyclerView;
    }

    public void setDialogPositiveButton(String textBtn, View.OnClickListener onClickListener){
        btnPositive.setVisibility(View.VISIBLE);
        btnPositive.setText(textBtn);
        btnPositive.setOnClickListener(onClickListener);
    }

    public void setDialogNegativeButton(String textBtn, View.OnClickListener onClickListener){
        btnNegative.setVisibility(View.VISIBLE);
        btnNegative.setText(textBtn);
        btnNegative.setOnClickListener(onClickListener);
    }

    public void setDialogNeutralButton(String textBtn, View.OnClickListener onClickListener){
        btnNeutral.setVisibility(View.VISIBLE);
        btnNeutral.setText(textBtn);
        btnNeutral.setOnClickListener(onClickListener);
    }

    public void showAlert(){

        dialog.show();

        // set the layout parameters so that dialog covers the entire screen, otherwise it wraps the elements
        try {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            dialog.show();
            dialog.getWindow().setAttributes(lp);
        } catch (Exception ignored){}
    }

    public void dismissAlert(){
        dialog.dismiss();
    }

    public void setCancellable(boolean cancellable){
        dialog.setCancelable(cancellable); // false: it wont close if back button is pressed
        dialog.setCanceledOnTouchOutside(cancellable);
    }

    public void setupHyperlinks(){
        //after show, we retrieve the message and add the hyperlinks
        tvMessage.setMovementMethod(LinkMovementMethod.getInstance());
//        tvMessage.setLinkTextColor(ctx.getResources().getColor(R.color.hyperlinks));
    }

    public void setOnCancelListener(Dialog.OnCancelListener onCancelListener){
        dialog.setOnCancelListener(onCancelListener);
    }
}
