package io.hammerhead.sample;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

public class EditProfileActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initBaseLayout(R.layout.activity_edit_profile);

        Intent intent = getIntent();
        String userName = intent.getStringExtra(KeyConstants.USER_NAME);
        int ftp = intent.getIntExtra(KeyConstants.USER_FTP, 0);
        int maxHr = intent.getIntExtra(KeyConstants.USER_MAX_HR, 0);
        EditText nameInput = findViewById(R.id.nameInput);
        EditText ftpInput = findViewById(R.id.ftpInput);
        EditText maxHrInput = findViewById(R.id.maxHrInput);

        nameInput.setText(userName);
        ftpInput.setText(String.valueOf(ftp));
        maxHrInput.setText(String.valueOf(maxHr));
    }

    @Override
    public void finish(){
        EditText nameInput = findViewById(R.id.nameInput);
        EditText ftpInput = findViewById(R.id.ftpInput);
        EditText maxHrInput = findViewById(R.id.maxHrInput);

        String userName = nameInput.getText().toString();
        if(userName.isEmpty()){
            userName = "User";
        }

        int ftp;
        int maxHr;
        try{
            ftp = Integer.parseInt(ftpInput.getText().toString());
        }
        catch(NumberFormatException e){
            ftp = 140;
        }
        try{
            maxHr = Integer.parseInt(maxHrInput.getText().toString());
        }
        catch(NumberFormatException e){
            maxHr = 180;
        }

        Intent intent = new Intent();
        intent.putExtra(KeyConstants.USER_NAME, userName);
        intent.putExtra(KeyConstants.USER_FTP, ftp);
        intent.putExtra(KeyConstants.USER_MAX_HR, maxHr);
        setResult(RESULT_OK, intent);
        super.finish();
    }
}
