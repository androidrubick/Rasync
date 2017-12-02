package pub.androidrubick.demo;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import butterknife.BindDrawable;
import butterknife.BindView;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.tv)
    TextView tv;
    @BindView(android.R.id.list)
    View v;

    @BindDrawable(R.mipmap.ic_launcher)
    Drawable ic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        Log.d("yytest", getResources().getResourceEntryName(R.mipmap.ic_launcher));
        Log.d("yytest", getResources().getResourceName(R.mipmap.ic_launcher));
    }

    private void initView() {
        tv = (TextView) findViewById(R.id.tv);
    }

    @OnClick(R.id.tv)
    void d() {

    }
}
