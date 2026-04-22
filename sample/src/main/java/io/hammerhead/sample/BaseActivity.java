package io.hammerhead.sample;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {

    protected void initBaseLayout(int contentLayoutId) {
        setContentView(R.layout.activity_base);
        LayoutInflater inflater = getLayoutInflater();
        View contentView = inflater.inflate(contentLayoutId, null);
        FrameLayout contentContainer = findViewById(R.id.contentLayout);
        contentContainer.removeAllViews();
        contentContainer.addView(contentView);

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

}
