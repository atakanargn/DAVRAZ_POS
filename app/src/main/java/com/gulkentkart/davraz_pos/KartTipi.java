package com.gulkentkart.davraz_pos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.telpo.tps550.api.printer.UsbThermalPrinter;
import com.telpo.tps550.api.util.StringUtil;
import com.telpo.tps550.api.util.SystemUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.client.Socket;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class KartTipi extends Activity {

    private Database myDb;
    private Socket mSocket;
    private String ipAdresi,socketPort,apiPort,istasyon_id,currentDateandTime;
    private ImageView wifi, logo;
    private TextView txtKartaYazilacak, DateTime;
    private int sayac=0;


    public static String qrcodeStr;
    private final int NOPAPER = 3;
    private final int LOWBATTERY = 4;
    private final int PRINTQRCODE = 7;
    private final int CANCELPROMPT = 10;
    private final int OVERHEAT = 12;
    private final int PRINTERR = 11;
    private String Result;
    private Boolean nopaper = false;
    private boolean LowBattery = false;
    private ProgressDialog progressDialog;
    KartTipi.QrHandler qrhandler;
    UsbThermalPrinter mUsbThermalPrinter = new UsbThermalPrinter(KartTipi.this);

    private Handler handler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_tip_secim);

        init_ui();

        qrhandler = new KartTipi.QrHandler();
        IntentFilter pIntentFilter = new IntentFilter();
        pIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        pIntentFilter.addAction("android.intent.action.BATTERY_CAPACITY_EVENT");
        registerReceiver(printReceive, pIntentFilter);

        // EĞER UYGULAMA İLK DEFA AÇILIYORSA, VARSAYILAN AYARLAR GELECEKTİR
        myDb = new Database(this);
        try {
            myDb.ayarEkle("posIsim","-");
            myDb.ayarEkle("kurulum","0");
            myDb.ayarEkle("istasyonID", "-");
            myDb.ayarEkle("sunucuIP", "34.133.68.189");
            myDb.ayarEkle("socketPort", "9872");
            myDb.ayarEkle("apiPort", "9091");
        }catch (Exception e){
            //
        }

        if(myDb.ayarGetir("kurulum").equals("0")){
            startActivity(new Intent(KartTipi.this,Settings.class));
        }

        ipAdresi   = "http://"+myDb.ayarGetir("sunucuIP");
        socketPort = myDb.ayarGetir("socketPort");
        apiPort   = myDb.ayarGetir("apiPort");

        try {
            IO.Options options = new IO.Options();
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(0, TimeUnit.MILLISECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(0, TimeUnit.MILLISECONDS);
            options.callFactory = clientBuilder.build();
            mSocket = IO.socket("http://34.133.68.189:9872",options);

        } catch (URISyntaxException e) {

        }

        istasyon_id = myDb.ayarGetir("istasyonID");

        socketeBaglan();

        final Handler handlerr = new Handler(Looper.getMainLooper());
        handlerr.postDelayed(new Runnable() {
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

                if(sayac%15==0) socketeBaglan();

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

                handlerr.postDelayed(this,1000);
            }
        }, 1000);

        final Handler veriIletimiTimer = new Handler(Looper.getMainLooper());
        veriIletimiTimer.postDelayed(new Runnable() {
            @SuppressLint("SimpleDateFormat")
            @Override
            public void run() {
                if(myDb.ayarGetir("kurulum").equals("1")){
                    guncelle();
                }
                veriIletimiTimer.postDelayed(this,10000);
            }
        }, 15000);
        tarifeGuncelle();
        handler = new Handler();
        final Runnable r = new Runnable() {
            public void run() {
                handler.postDelayed(this, 1000);
            }
        };

    }

    void socketeBaglan(){
        // SOCKETIO AYARLARI

        try{
            IO.Options options = new IO.Options();
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(1000, TimeUnit.MILLISECONDS)
                    .readTimeout(1000, TimeUnit.MILLISECONDS)
                    .writeTimeout( 1000, TimeUnit.MILLISECONDS);
            options.callFactory = clientBuilder.build();

            mSocket = IO.socket(ipAdresi + ":" + socketPort, options);

            if(!mSocket.connected()) {
                mSocket.connect();
                mSocket.emit("sendDevice", istasyon_id);
            }
        }catch (Exception e) {
            // Toast.makeText(KartTipi.this,"Cihaz çevrimdışı modda çalışıyor.",Toast.LENGTH_LONG).show();
        }
    }

    public void init_ui(){
        Button bilet_bas = (Button) findViewById(R.id.bilet_bas);

        bilet_bas.setOnClickListener(v -> {

            try{
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("stationID", istasyon_id);
                    jsonObject.put("tarih",new SimpleDateFormat("dd-MM-yyyy\nHH:mm:ss").format(new Date()));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(JSON, jsonObject.toString());

                OkHttpClient client = new OkHttpClient();
                String url = "http://34.133.68.189:9091/api/qrticket/";
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        // TODO OFFLINE QR EKLEME
                    }
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        final String myResponse = response.body().string();
                        qrcodeStr = myResponse;
                        if (qrcodeStr == null || qrcodeStr.length() == 0) {
                            Toast.makeText(KartTipi.this, getString(R.string.input_print_data), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (LowBattery == true) {
                            qrhandler.sendMessage(qrhandler.obtainMessage(LOWBATTERY, 1, 0, null));
                        } else {
                            if (!nopaper) {
                                qrhandler.sendMessage(qrhandler.obtainMessage(PRINTQRCODE, 1, 0, null));
                            } else {
                                Toast.makeText(KartTipi.this, getString(R.string.printInit), Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                });
            }catch (Exception e){
                Log.w("QRKOD OLUŞTURMA HATASI",e);
            }
        });



        txtKartaYazilacak = (TextView) findViewById(R.id.textView);
        DateTime = (TextView) findViewById(R.id.DateTime);

        wifi = (ImageView) findViewById(R.id.wifi);

        logo = (ImageView) findViewById(R.id.logo);

        logo.setOnClickListener(v -> {
            tarifeGuncelle();
            //startActivity(new Intent(KartTipi.this,Settings.class));
        });

    }

    public void kart_iade(View view) {
        startActivity(new Intent(KartTipi.this,Kart_Iade.class));
    }

    private class QrHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case NOPAPER:
                    noPaperDlg();
                    break;
                case LOWBATTERY:
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(KartTipi.this);
                    alertDialog.setTitle(R.string.operation_result);
                    alertDialog.setMessage(getString(R.string.LowBattery));
                    alertDialog.setPositiveButton(getString(R.string.dialog_comfirm), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    });
                    alertDialog.show();
                    break;
                case PRINTQRCODE:
                    new KartTipi.qrcodePrintThread().start();
                    break;
                case OVERHEAT:
                    AlertDialog.Builder overHeatDialog = new AlertDialog.Builder(KartTipi.this);
                    overHeatDialog.setTitle(R.string.operation_result);
                    overHeatDialog.setMessage(getString(R.string.overTemp));
                    overHeatDialog.setPositiveButton(getString(R.string.dialog_comfirm), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    });
                    overHeatDialog.show();
                    break;
                case CANCELPROMPT:
                    if (progressDialog != null && !KartTipi.this.isFinishing()) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                    break;
                default:
                    Toast.makeText(KartTipi.this, "Print Error!", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
    private void noPaperDlg() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(KartTipi.this);
        dlg.setTitle(getString(R.string.noPaper));
        dlg.setMessage(getString(R.string.noPaperNotice));
        dlg.setCancelable(false);
        dlg.setPositiveButton(R.string.sure, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });
        dlg.show();
    }
    public Bitmap CreateCode(String str, com.google.zxing.BarcodeFormat type, int bmpWidth, int bmpHeight) throws WriterException {
        Hashtable<EncodeHintType,String> mHashtable = new Hashtable<EncodeHintType,String>();
        mHashtable.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = new MultiFormatWriter().encode(str, type, bmpWidth, bmpHeight, mHashtable);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    pixels[y * width + x] = 0xff000000;
                } else {
                    pixels[y * width + x] = 0xffffffff;
                }
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
    private class qrcodePrintThread extends Thread {
        @Override
        public void run() {
            super.run();
            try {
                mUsbThermalPrinter.reset();
                int printGray = 4;
                mUsbThermalPrinter.setGray(printGray);
                Bitmap bitmap = CreateCode(qrcodeStr, BarcodeFormat.QR_CODE, 420, 420);
                if(bitmap != null){
                    mUsbThermalPrinter.printLogo(bitmap, true);
                }
                SimpleDateFormat sdf = new SimpleDateFormat(" dd.MM.yyyy \n HH:mm:ss ");
                String mDate = sdf.format(new Date());
                mUsbThermalPrinter.addString(mDate);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(20);
            } catch (Exception e) {
                e.printStackTrace();
                Result = e.toString();
                if (Result.equals("com.telpo.tps550.api.printer.NoPaperException")) {
                    nopaper = true;
                } else if (Result.equals("com.telpo.tps550.api.printer.OverHeatException")) {
                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                } else {
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }
            } finally {
                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                if (nopaper){
                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                    nopaper = false;
                    return;
                }
            }
        }
    }
    private final BroadcastReceiver printReceive = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_NOT_CHARGING);
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
                //TPS390 can not print,while in low battery,whether is charging or not charging
                if(SystemUtil.getDeviceType() == StringUtil.DeviceModelEnum.TPS390.ordinal()){
                    if (level * 5 <= scale) {
                        LowBattery = true;
                    } else {
                        LowBattery = false;
                    }
                }else {
                    if (status != BatteryManager.BATTERY_STATUS_CHARGING) {
                        if (level * 5 <= scale) {
                            LowBattery = true;
                        } else {
                            LowBattery = false;
                        }
                    } else {
                        LowBattery = false;
                    }
                }
            }
            //Only use for TPS550MTK devices
            else if (action.equals("android.intent.action.BATTERY_CAPACITY_EVENT")) {
                int status = intent.getIntExtra("action", 0);
                int level = intent.getIntExtra("level", 0);
                if(status == 0){
                    if(level < 1){
                        LowBattery = true;
                    }else {
                        LowBattery = false;
                    }
                }else {
                    LowBattery = false;
                }
            }
        }
    };

    public void tarifeCek(){
        try{
            OkHttpClient client = new OkHttpClient();
            String url = ipAdresi+":"+apiPort+"/api/type/pos";
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {

                }
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                    if (response.isSuccessful()) {
                        assert response.body() != null;
                        final String myResponse = response.body().string();
                        KartTipi.this.runOnUiThread(() -> {
                            try {
                                myDb.deleteAll("priceSchedule");
                                String[] parts = myResponse.split(",");
                                int i=0;
                                for (String tarife : parts) {
                                    i=i+1;
                                    myDb.tarifeEkle(i,tarife,"MESAJ","0","tekli");
                                }
                                Log.i("Güncelleme", "5 - TARIFELER");
                                tarifeGuncelle();
                            } catch (Exception e) {
                                Log.w("Güncelleme",e);
                            }
                        });
                    }
                }
            });
        }catch (Exception e){
            Log.w("Güncelleme",e);
        }
    }

    void tarifeGuncelle(){
        LinearLayout layout = (LinearLayout) findViewById(R.id.myLayout0);
        layout.removeAllViews();

        int sayac=0;
        String gelenTip = myDb.tarifeGetir(sayac);
        while(gelenTip!="-1"){
            gelenTip = myDb.tarifeGetir(sayac);
            if(gelenTip=="-1"){
                break;
            }
            Button btnTag = new Button(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(16,8,8,8);
            params.width=690;
            btnTag.setBackgroundResource(R.drawable.button_number_violet_shape);
            btnTag.setTextColor(Color.parseColor("#FFFFFF"));
            btnTag.setTextSize(18);
            btnTag.setLayoutParams(params);
            btnTag.setText(gelenTip.toUpperCase());

            String finalGelenTip = gelenTip;
            btnTag.setOnClickListener(v -> {
                String kart_tipi =  finalGelenTip.toLowerCase();
                Intent satisSayfasi=new Intent(KartTipi.this,KartSatis.class);
                satisSayfasi.putExtra("kart_tipi",kart_tipi);
                startActivity(satisSayfasi);
                finish();
            });

            layout.addView(btnTag);
            sayac++;
        }
    }

    public void guncelle(){
        tanimKartlarGuncelle();
    }

    public void tanimKartlarGuncelle(){
        try{
            OkHttpClient client = new OkHttpClient();
            String url = ipAdresi+":"+apiPort+"/api/card/tanimli";
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {

                }
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                    if (response.isSuccessful()) {
                        assert response.body() != null;
                        final String myResponse = response.body().string();
                        KartTipi.this.runOnUiThread(() -> {
                            try {
                                String[] parts = myResponse.split(",");
                                for (String uid : parts) {
                                    myDb.kartEkle(uid);
                                }
                                Log.i("Güncelleme", "1 - TANIMLI KARTLAR");
                                blacklistGuncelle();
                            } catch (Exception e) {
                                Log.w("Güncelleme",e);
                            }
                        });
                    }
                }
            });
        }catch (Exception e){
            Log.w("Güncelleme",e);
        }
    }

    public void blacklistGuncelle(){
        try{
            // BLACKLIST TEST
            OkHttpClient client = new OkHttpClient();
            String url = ipAdresi+":"+apiPort+"/api/card/2";
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {

                }
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {


                    if (response.isSuccessful()) {
                        assert response.body() != null;
                        final String myResponse = response.body().string();
                        KartTipi.this.runOnUiThread(() -> {
                            try {
                                myDb.deleteAll("blacklist");

                                String[] parts = myResponse.split(",");
                                for (String uid : parts) {
                                    myDb.blacklistInsert(uid);
                                }

                                Log.i("Güncelleme", "3 - KARALİSTE");

                                tarifeCek();
                            } catch (Exception e) {
                                Log.w("Güncelleme",e);
                            }

                        });

                    }
                }
            });
        }catch (Exception e){
            Log.w("Güncelleme",e);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    public void tip(View view) {
        Log.i("BUTON","1");
            String kart_tipi = ((Button) view).getText().toString().toLowerCase();
            Intent satisSayfasi=new Intent(KartTipi.this,KartTipi.class);
            satisSayfasi.putExtra("kart_tipi",kart_tipi);
            startActivity(satisSayfasi);
            finish();
    }

    public void sezonluk(View view) {
        Intent satisSayfasi=new Intent(KartTipi.this,MusteriEkle.class);
        startActivity(satisSayfasi);
    }
}
