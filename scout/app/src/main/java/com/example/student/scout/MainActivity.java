package com.example.student.scout;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    ImageButton btnInfo;
    TextView textCO, textState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnInfo = (ImageButton) findViewById(R.id.btnInfo);
        textCO = (TextView) findViewById(R.id.textCO);
        textState = (TextView) findViewById(R.id.textState);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("일산화탄소 농도별 증상");
        builder.setIcon(R.drawable.owl);
        LayoutInflater factory = LayoutInflater.from(MainActivity.this);
        final View view = factory.inflate(R.layout.sample, null);
        builder.setView(view);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

       btnInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.show();
            }
        });

       Intent intent = getIntent();
       int co = intent.getIntExtra("Value", -1);
       String state = intent.getStringExtra("State");

       if(co!= -1){
           textCO.setText(Integer.toString(co)+"ppm");

           switch (state){
               case "SAFE":
                   textState.setTextColor(Color.GREEN);
                   break;
               case "NORMAL":
                   textState.setTextColor(Color.LTGRAY);
                   break;
               case "CAUTION":
                   textState.setTextColor(Color.YELLOW);
                   break;
               case "WARNING":
                   textState.setTextColor(Color.MAGENTA);
                   break;
               case "DANGER":
                   textState.setTextColor(Color.RED);
                   break;
           }
           textState.setText(state);
       }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.itemContact:
                Intent intent1 = new Intent(getApplicationContext(),ContactActivity.class);
                startActivity(intent1);
                break;
            case R.id.itemBluetooth:
                Intent intent2 = new Intent(getApplicationContext(),BluetoothActivity.class);
                startActivity(intent2);
                break;
        }
        return super.onOptionsItemSelected(item);
    }


}
