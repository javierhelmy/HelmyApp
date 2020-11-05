package com.taedison.helmy;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

/***
 * Class used for launching alertDialogs that include an image using a customized layout.
 * This class is used for describing visually what the wheel reference and the backup password means
 * in HelmyM
 */
public class AlertImage {

    private Dialog dialog;
    private TextView tvMessage, btnCancel;
    private ImageView image;
    private TextView btnPositive, btnNegative, btnNeutral;
    private Context ctx;

    AlertImage(Context context){
        ctx = context;
        dialog = new Dialog(context);
        dialog.setContentView(R.layout.alert_image); // customized layout

        btnCancel = dialog.findViewById(R.id.btnCancelAlert);
        tvMessage = dialog.findViewById(R.id.tvAlertMessage);
        image = dialog.findViewById(R.id.imgAlert);
        btnPositive = dialog.findViewById(R.id.btnPositive);
        btnNegative = dialog.findViewById(R.id.btnNegative);
        btnNeutral = dialog.findViewById(R.id.btnNeutral);

        tvMessage.setVisibility(View.GONE);
        image.setVisibility(View.GONE);
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

    public void setDialogImage(Drawable drawable){
        image.setVisibility(View.VISIBLE);
        image.setImageDrawable(drawable);
    }

    public ImageView getDialogImage(){
        return image;
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

    public void setDialogOnDismissListener(Dialog.OnDismissListener onDismissListener){
        dialog.setOnDismissListener(onDismissListener);
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
    }
}
