package com.taedison.helmy;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;

/***
 * Class for implementing customized spinner
 */
public class ClassSpinnerAdapter extends ArrayAdapter {

    private ArrayList<String> arrayList;
    private Context context;
    private Spinner spinner;
    private boolean nothingSelectedError = false;

    ClassSpinnerAdapter(final Context context, int textViewResourceId, ArrayList strings, final Spinner spinner) {
        super(context, textViewResourceId, strings);
        arrayList = strings;
        this.context = context;
        this.spinner = spinner; // Spinner view is part of the constructor so that we can update it if user clicks "other" to add another item in the spinner
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                if(spinner.getItemAtPosition(pos).equals(
                        context.getResources().getString(R.string.otherBrand) )){
                    // check if user selected the option "other" so that he/she can input another
                    launchAlertEnterOther();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    ClassSpinnerAdapter(final Context context, int textViewResourceId, ArrayList strings, final Spinner spinner, boolean nothingSelectedError) {
        // constructor used to instantiate a new spinner with red color to show user that he/she needs
        // to select an option
        super(context, textViewResourceId, strings);
        arrayList = strings;
        this.context = context;
        this.spinner = spinner; // Spinner view is part of the constructor so that we can update it if user clicks "other" to add another item in the spinner
        this.nothingSelectedError = nothingSelectedError;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                if(spinner.getItemAtPosition(pos).equals(
                        context.getResources().getString(R.string.otherBrand) )){
                    // check if user selected the option "other" so that he/she can input another
                    launchAlertEnterOther();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        return setSpinnerLayout_n_Hint(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent)
    {
        return setItemsLayout(position, convertView, parent);
    }

    private View setSpinnerLayout_n_Hint(int position, View convertView, ViewGroup parent)
    {
        // Inflating the layout for the custom Spinner
        LayoutInflater inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        View layout = inflater.inflate(R.layout.textview_template_spinner, parent, false);
        // getting the textview fromActivity the inflated layout
        TextView textView = (TextView) layout.findViewById(R.id.tvTemplate);
        // Setting the text using the array
        textView.setText(arrayList.get(position));
        textView.setGravity(Gravity.CENTER);
        //gray out the first item which will act as the hint of the spinner
        //this first item must also be disabled so that it is not selected, see isEnabled method
        if (position == 0) {
            // Set the hint text color gray
            textView.setTextColor(context.getResources().getColor(R.color.textHintColor) );
            if(nothingSelectedError){
                textView.setBackgroundResource(R.drawable.redcontour_rounded);
            }
        } else {
            textView.setTextColor(context.getResources().getColor(R.color.textColorEdit) );
        }
        return layout;
    }

    private View setItemsLayout(int position, View convertView, ViewGroup parent)
    {
        // Inflating the layout for the custom Spinner
        LayoutInflater inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        View layout = inflater.inflate(R.layout.textview_template_spinner, parent, false);
        // Getting in the inflated layout
        TextView textView = (TextView) layout.findViewById(R.id.tvTemplate);
        // Setting the text using the array
        textView.setText(arrayList.get(position));
        textView.setGravity(Gravity.CENTER);
        //gray out the first item which will act as the hint of the spinner
        //this first item must also be disabled so that it is not selected, see isEnabled method
        if (position == 0) {
            // Set the hint text color gray
            textView.setTextColor(context.getResources().getColor(R.color.textHintColor));
        } else {
            textView.setTextColor(Color.BLACK);
        }
        return layout;
    }



    private void launchAlertEnterOther(){
        final AlertMessageButton alert = new AlertMessageButton(context);
        alert.setDialogEditText(arrayList.get(0));
        alert.setDialogPositiveButton(context.getResources().getString(R.string.Ok), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(TextUtils.isEmpty(alert.getText_editText())){
                    spinner.setSelection(0);
                } else {
                    String other = alert.getText_editText();
                    addOther(other);
                }
                alert.dismissAlert();
            }
        });
        alert.setDialogNegativeButton(context.getResources().getString(R.string.cancel), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                spinner.setSelection(0);

                alert.dismissAlert();
            }
        });
        alert.setDialogCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                spinner.setSelection(0);
            }
        });
        alert.setCancelButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                spinner.setSelection(0);
                alert.dismissAlert();
            }
        });
        alert.showAlert();
        alert.setFocusOnEditText();

    }

    public void changeAdapterBaseOnErrorNotSelected (boolean error){
        // method used to instantiate a new spinner with red color to show user that he/she needs
        // to select an option
        if(error){
            spinner.setAdapter(new ClassSpinnerAdapter(context,
                    R.layout.textview_template_spinner, arrayList, spinner, true));
        } else {
            int selected = spinner.getSelectedItemPosition();
            spinner.setAdapter(new ClassSpinnerAdapter(context,
                    R.layout.textview_template_spinner, arrayList, spinner, false));
            spinner.setSelection(selected);
        }
    }

    public void addOther(String other){
        // add a new item as a result of selecting the option "other"
        arrayList.add(arrayList.size() - 1, other);
        spinner.setAdapter(new ClassSpinnerAdapter(context,
                R.layout.textview_template_spinner, arrayList, spinner, false));
        spinner.setSelection(arrayList.indexOf(other));
    }
}
