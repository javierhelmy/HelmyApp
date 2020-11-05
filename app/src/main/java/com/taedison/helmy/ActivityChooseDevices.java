package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import java.util.ArrayList;

/***
 * Activity lists the helmy devices so that user can choose, edit or add a new one
 */
public class ActivityChooseDevices extends AppCompatActivity {

    final String TAG = "chooseDevices";

    //Views
    TextView tvHelmets, tvBikes;
    TextView btnHelmet, btnBike;

    //adapters
    ArrayList<String> arrayHelmetsNicknames, arrayBikesNicknames;
    String primaryHelmetSelectedNickname, primaryBikeSelectedNickname;

    //shared preferences
    SingletonSharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_devices);

        //views
        tvHelmets = findViewById(R.id.tvHelmetSelected);
        tvBikes = findViewById(R.id.tvBikeSelected);
        btnHelmet = findViewById(R.id.btnHelmet);
        btnBike = findViewById(R.id.btnBike);

        //shared preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext()); // for the last user logged in

        // Initialize Items for the Helmet adapter
        arrayHelmetsNicknames = preferences.get_helmets_saved_nicknames();
        primaryHelmetSelectedNickname =  preferences.get_primary_helmet_nickname();
        if(primaryHelmetSelectedNickname != null){
            tvHelmets.setText(primaryHelmetSelectedNickname);
            tvHelmets.setTextColor(preferences.getHelmetColor(preferences.get_helmet_MAC_from_nickname(primaryHelmetSelectedNickname)));
        } else if(preferences.get_helmets_saved_MACs().size() > 0){
            // there are more helmet but user has not selected one
            tvHelmets.setText(getResources().getString(R.string.selectHelmet));
        }

        // Initialize Items for the Bike adapter
        arrayBikesNicknames = preferences.get_bikes_saved_nicknames();
        primaryBikeSelectedNickname =  preferences.get_primary_bike_nickname();
        if(primaryBikeSelectedNickname != null){
            tvBikes.setText(primaryBikeSelectedNickname);
        } else if(preferences.get_bikes_saved_MACs().size() > 0){
            // there are more bikes but user has not selected one
            tvBikes.setText(getResources().getString(R.string.selectBike));
        }

    }



    public void selectPrimaryHelmet(View view) {
        if(arrayHelmetsNicknames.size() > 0){
            final AdapterListDevices adapterHelmets = new AdapterListDevices(arrayHelmetsNicknames, this);

            final AlertList alert = new AlertList(this);
            alert.setRecyclerView(adapterHelmets);

            final RecyclerView recyclerView = alert.getRecyclerView();
            adapterHelmets.setOnItemClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int position = recyclerView.getChildAdapterPosition(view);
                    adapterHelmets.HighlightView(position);
                    adapterHelmets.notifyDataSetChanged();

                    primaryHelmetSelectedNickname = arrayHelmetsNicknames.get(position);
                    tvHelmets.setTextColor(preferences.getHelmetColor(preferences.get_helmet_MAC_from_nickname(primaryHelmetSelectedNickname)));
                }
            });

            alert.setDialogNegativeButton(getResources().getString(R.string.edit),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if(primaryHelmetSelectedNickname != null) {
                                Intent intent = new Intent(ActivityChooseDevices.this, ActivityRegisterHelmet.class);
                                intent.putExtra("edit_MAC_helmet", preferences.get_helmet_MAC_from_nickname(primaryHelmetSelectedNickname) );
                                startActivity(intent);
                            }

                            alert.dismissAlert();
                        }
                    });
            alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if(primaryHelmetSelectedNickname != null) {
                                tvHelmets.setText(primaryHelmetSelectedNickname);
                                preferences.savePrimaryHelmetPreferences( preferences.get_helmet_MAC_from_nickname(primaryHelmetSelectedNickname) );
                            }
                            alert.dismissAlert();
                        }
                    });
            alert.setDialogNeutralButton(getResources().getString(R.string.addNewHelmet),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // add new bluetoothDevice
                            Intent intent = new Intent(ActivityChooseDevices.this, ActivityRegisterHelmet.class);
                            startActivity(intent);
                            alert.dismissAlert();
                        }
                    });
            alert.showAlert();

        } else {
            // no HelmyCs saved. Add a new one
            Intent intent = new Intent(ActivityChooseDevices.this, ActivityRegisterHelmet.class);
            startActivity(intent);
        }
    }

    public void selectPrimaryBike(View view) {
        if(arrayBikesNicknames.size() > 0){
            final AdapterListDevices adapterBikes = new AdapterListDevices(arrayBikesNicknames, this);

            final AlertList alert = new AlertList(this);
            alert.setRecyclerView(adapterBikes);

            final RecyclerView recyclerView = alert.getRecyclerView();
            adapterBikes.setOnItemClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int position = recyclerView.getChildAdapterPosition(view);
                    adapterBikes.HighlightView(position);
                    adapterBikes.notifyDataSetChanged();

                    primaryBikeSelectedNickname = arrayBikesNicknames.get(position);
                }
            });

            alert.setDialogNegativeButton(getResources().getString(R.string.edit),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if(primaryBikeSelectedNickname != null) {
                                Intent intent = new Intent(ActivityChooseDevices.this, ActivityRegisterBike.class);
                                intent.putExtra("edit_MAC_bike", preferences.get_bike_MAC_from_nickname(primaryBikeSelectedNickname));
                                startActivity(intent);
                            }
                            alert.dismissAlert();
                        }
                    });
            alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if(primaryBikeSelectedNickname != null) {
                                tvBikes.setText(primaryBikeSelectedNickname);
                                preferences.savePrimaryBikePreferences( preferences.get_bike_MAC_from_nickname(primaryBikeSelectedNickname) );
                            }
                            alert.dismissAlert();
                        }
                    });
            alert.setDialogNeutralButton(getResources().getString(R.string.addNewBike),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // add new bluetoothDevice
                            Intent intent = new Intent(ActivityChooseDevices.this, ActivityRegisterBikeOCR.class);
                            startActivity(intent);
                            alert.dismissAlert();
                        }
                    });
            alert.showAlert();
        } else {
            // no HelmyMs saved. Add a new one
            Intent intent = new Intent(ActivityChooseDevices.this, ActivityRegisterBikeOCR.class);
            startActivity(intent);
        }

    }

    public void click_btnDevicesSelected(View view) {
        // method registered in the layout of the activity
        Intent intent = new Intent(this, ActivityGoAs.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

}
