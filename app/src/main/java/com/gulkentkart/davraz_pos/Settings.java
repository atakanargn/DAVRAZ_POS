package com.gulkentkart.davraz_pos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import okhttp3.OkHttpClient;

public class Settings extends Activity {

    private TextView DateTime;
    private int sayac;
    private String currentDateandTime;
    private ImageView wifi, logo;

    private EditText istasyonID_Edit,sunucuIP_edit,socketPort_edit,apiPort_edit,turnikeBekleme_edit,kartBekleme_edit;
    private Database myDb;
    private TextView stationID;
    private Button kurulumBtn;
    private Socket mSocket;

    private final Emitter.Listener onSetup = new Emitter.Listener() {
        @SuppressLint("SetTextI18n")
        @Override
        public void call(final Object... args) {
            Settings.this.runOnUiThread(() -> {
                String turnikeDelay = (String) args[0].toString();
                String cardDelay = (String) args[1].toString();
                String posIsim = (String) args[2].toString();
                try {
                    myDb.ayarDuzenle("posIsim",posIsim);
                    myDb.ayarDuzenle("istasyonID",istasyonID_Edit.getText().toString());
                    myDb.ayarDuzenle("kurulum","1");
                    kurulumBtn.setEnabled(false);
                    kurulumBtn.setText("Değiştir");
                    istasyonID_Edit.setEnabled(false);
                    alertDialog("Kurulum","Kurulum başarıyla tamamlandı.");


                } catch (Exception e) {
                    //return;
                }
            });
        }
    };

    private final Emitter.Listener onUpdate = new Emitter.Listener() {
        @SuppressLint("SetTextI18n")
        @Override
        public void call(final Object... args) {
            Settings.this.runOnUiThread(() -> {
                String turnikeDelay = (String) args[0].toString();
                String cardDelay = (String) args[1].toString();
                String turnikeIsim = (String) args[2].toString();
                try {
                    turnikeBekleme_edit.setText(turnikeDelay);
                    kartBekleme_edit.setText(cardDelay);
                    myDb.ayarDuzenle("turnikeIsim",turnikeIsim);
                    myDb.ayarDuzenle("turnikeBekleme",turnikeDelay);
                    myDb.ayarDuzenle("kartBekleme",cardDelay);
                    myDb.ayarDuzenle("istasyonID",istasyonID_Edit.getText().toString());
                    myDb.ayarDuzenle("kurulum","1");
                    kurulumBtn.setEnabled(false);
                    kurulumBtn.setText("Değiştir");
                    istasyonID_Edit.setEnabled(false);
                    alertDialog("Kurulum","Kurulum başarıyla tamamlandı.");
                } catch (Exception e) {
                    //return;
                }
            });
        }
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_settings);
        initUI();

        myDb = new Database(this);
        // EĞER UYGULAMA İLK DEFA AÇILIYORSA, VARSAYILAN AYARLAR GELECEKTİR
        try {
            myDb.ayarEkle("posIsim","-");
            myDb.ayarEkle("kurulum","0");
            myDb.ayarEkle("istasyonID", "-");
            myDb.ayarEkle("sunucuIP", "34.133.68.189");
            myDb.ayarEkle("socketPort", "9872");
            myDb.ayarEkle("apiPort", "9091");
        }catch (Exception e){
            Log.w("Veritabanı",e);
        }

        String ipAdresi = "http://" + myDb.ayarGetir("sunucuIP") + ":" + myDb.ayarGetir("socketPort");
        Log.w("IP", ipAdresi);

        if(myDb.ayarGetir("kurulum").equals("0")){
            kurulumBtn.setEnabled(true);
        }else{
            kurulumBtn.setEnabled(false);

            kurulumBtn.setText("Değiştir");
            istasyonID_Edit.setEnabled(false);
        }

        istasyonID_Edit.setText(myDb.ayarGetir("istasyonID"));
        String istasyon_id = myDb.ayarGetir("istasyonID");
        sunucuIP_edit.setText(myDb.ayarGetir("sunucuIP"));
        socketPort_edit.setText(myDb.ayarGetir("socketPort"));
        apiPort_edit.setText(myDb.ayarGetir("apiPort"));
        try{


            IO.Options options = new IO.Options();
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(0, TimeUnit.MILLISECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(0, TimeUnit.MILLISECONDS);
            options.callFactory = clientBuilder.build();
            mSocket = IO.socket(ipAdresi,options);
            if(myDb.ayarGetir("kurulum").equals("0")) {
                mSocket.on("setupFinish", onSetup);
            }else if(myDb.ayarGetir("kurulum").equals("1")) {
                mSocket.on("setupUpdate", onUpdate);
            }
            mSocket.connect();
        }catch (Exception e) {
            Toast.makeText(Settings.this,"Cihaz çevrimdışı modda çalışıyor.",Toast.LENGTH_LONG).show();
        }
    }

    private void initUI() {
        kurulumBtn = (Button) findViewById(R.id.kurulumBtn);

        istasyonID_Edit = (EditText) findViewById(R.id.istasyonID_edit);
        sunucuIP_edit   = (EditText) findViewById(R.id.sunucuIP_edit);
        socketPort_edit = (EditText) findViewById(R.id.socketPort_edit);
        apiPort_edit    = (EditText) findViewById(R.id.apiPort_edit);

        DateTime = (TextView) findViewById(R.id.DateTime);
        wifi = (ImageView) findViewById(R.id.wifi);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @SuppressLint("SimpleDateFormat")
            @Override
            public void run() {


                try{
                    ConnectivityManager cm = (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                    @SuppressLint("MissingPermission") NetworkInfo nInfo = cm.getActiveNetworkInfo();
                    boolean isWiFi = nInfo.getType() == ConnectivityManager.TYPE_WIFI;

                    wifi.setVisibility((isWiFi ?View.VISIBLE:View.INVISIBLE));
                }catch (Exception e){
                    wifi.setVisibility(View.INVISIBLE);
                }

                currentDateandTime = new SimpleDateFormat("dd-MM-yyyy\nHH:mm:ss").format(new Date());
                DateTime.setText(currentDateandTime);

                sayac=sayac+1;

                View decorView = getWindow().getDecorView();
                int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;
                decorView.setSystemUiVisibility(uiOptions);

                handler.postDelayed(this,1000);
            }
        }, 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NFCForegroundUtil();
    }

    public void NFCForegroundUtil()
    {
        Activity activity = Settings.this;
        NfcAdapter mAdapter = NfcAdapter.getDefaultAdapter(activity
                .getApplicationContext());

        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent mPendingIntent = PendingIntent.getActivity(activity, 0, new Intent(
                        activity, activity.getClass())
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                0);

        // See below
        mAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
    }

    public void kaydet(View view) {
        try{
            myDb.ayarDuzenle("istasyonID", istasyonID_Edit.getText().toString());
            myDb.ayarDuzenle("sunucuIP", sunucuIP_edit.getText().toString());
            myDb.ayarDuzenle("socketPort", socketPort_edit.getText().toString());
            myDb.ayarDuzenle("apiPort", apiPort_edit.getText().toString());
            myDb.ayarDuzenle("turnikeBekleme", turnikeBekleme_edit.getText().toString());
            myDb.ayarDuzenle("kartBekleme", kartBekleme_edit.getText().toString());
            String istasyon_id = myDb.ayarGetir("istasyonID");
            stationID.setText(istasyon_id);
            alertDialog("Ayarlar","Yeni ayarlar başarıyla kaydedildi.");
        }catch (Exception e){
            alertDialog("Ayarlar","Bir hata oluştu.");
        }
    }

    public void alertDialog(String title, String message){
        runOnUiThread(() -> new AlertDialog.Builder(Settings.this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Tamam", (dialog, which) -> {
                    // Whatever...
                }).show());
    }

    public void kurulumMod(View view) {
        mSocket.emit("setupDeviceAndroid", istasyonID_Edit.getText());
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(Settings.this, KartTipi.class));
        finish();
    }

}
