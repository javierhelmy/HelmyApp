package com.taedison.helmy;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

/***
 * Class used for launching alertDialogs that include text and three buttons (positive, negative and
 * neutral) using a customized layout.
 * This class is used for displaying information or asking the user for input
 */
public class AlertMessageButton {

    private Dialog dialog;
    private TextView tvMessage, btnCancel;
    private EditText editText;
    private TextView btnPositive, btnNegative, btnNeutral;
    private Context ctx;

    public AlertMessageButton(Context context){
        ctx = context;
        dialog = new Dialog(context);
        dialog.setContentView(R.layout.alert_message_button); // customized layout

        btnCancel = dialog.findViewById(R.id.btnCancelAlert);
        tvMessage = dialog.findViewById(R.id.tvAlertMessage);
        editText = dialog.findViewById(R.id.etAlertEditText);
        btnPositive = dialog.findViewById(R.id.btnPositive);
        btnNegative = dialog.findViewById(R.id.btnNegative);
        btnNeutral = dialog.findViewById(R.id.btnNeutral);

        tvMessage.setVisibility(View.GONE);
        editText.setVisibility(View.GONE);
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

    public void setCancelButtonClickListener(View.OnClickListener onClickListener){
        btnCancel.setOnClickListener(onClickListener);
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

    public void setDialogEditText(String hint){
        // this method is so that the hyperlinks work
        editText.setVisibility(View.VISIBLE);
        editText.setHint(hint);
    }

    public void setFocusOnEditText(){
        editText.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    public String getText_editText(){
        return editText.getText().toString();
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

    public void setDialogCancelListener(DialogInterface.OnCancelListener onCancelListener){
        dialog.setOnCancelListener(onCancelListener);
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
}
