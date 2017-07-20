package edu.cmu.hw8byadav;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SecondActivity extends AppCompatActivity {
    public static final String manualUseInput = "Manual Input";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        final EditText et = (EditText)findViewById(R.id.input_isbn);

        Button getDetails = (Button) findViewById(R.id.get_details);
        getDetails.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userInputISBN = et.getText().toString();
                //TODO
                // add more validation for user isbn input
                if(userInputISBN.equals("") || userInputISBN.length() == 0){
                    Toast.makeText(SecondActivity.this, "Please enter ISBN", Toast.LENGTH_SHORT).show();
                }
                else {
                    //start ISBNActivity
                    Intent i = new Intent(SecondActivity.this, ISBNActivity.class);
                    i.putExtra(SecondActivity.manualUseInput, userInputISBN);
                    startActivity(i);
                }
            }
        });
    }
}
