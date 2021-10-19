package com.gulkentkart.davraz_pos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.util.Base64;
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
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.socket.client.IO;
import io.socket.client.Socket;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class KartSatis extends Activity {

    private NfcAdapter mNfcAdapter;
    private PendingIntent mPendingIntent;

    private TextView txtKartaYazilacak, DateTime;
    private int writeMode;
    private LinearLayout linearLayout;

    private final int red = Color.parseColor("#66FF0000");
    private final int green = Color.parseColor("#6626FF00");
    private final int white = Color.parseColor("#66FFFFFF");
    private int durum = 0;
    private Handler handler;

    private ImageView wifi, logo;

    private Socket mSocket;
    private String istasyon_id,basilanID,currentDateandTime,onceki,sonraki;

    private Button btn1,btn2,btn3,btn4,btn5,btn6,btn7,btn8,btn9,karta_yukle,sifirla,bilet_bas;
    private int miktar=0;

    private boolean fis_yazdir=false;
    private int sayac=0;

    private Database myDb;
    private String ipAdresi,socketPort,apiPort;

    private String refId;

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
    KartSatis.QrHandler qrhandler;
    UsbThermalPrinter mUsbThermalPrinter = new UsbThermalPrinter(KartSatis.this);
    private String kart_tipi;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            startActivity(new Intent(KartSatis.this,KartTipi.class));
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_kart_satis);

        Intent intent=getIntent();
        kart_tipi = intent.getStringExtra("kart_tipi");


        initUI();

        qrhandler = new KartSatis.QrHandler();
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

        ipAdresi   = "http://"+myDb.ayarGetir("sunucuIP");
        socketPort = myDb.ayarGetir("socketPort");
        apiPort   = myDb.ayarGetir("apiPort");

        if(myDb.ayarGetir("kurulum").equals("0")){
            startActivity(new Intent(KartSatis.this,Settings.class));
        }

        istasyon_id = myDb.ayarGetir("istasyonID");

        Log.e("IP",istasyon_id);

        NfcManager mNfcManager = (NfcManager) getSystemService(Context.NFC_SERVICE);
        mNfcAdapter = mNfcManager.getDefaultAdapter();
        if (mNfcAdapter == null) {
            //show_nfc_message.setText(R.string.tv_nfc_notsupport);
        } else if ((mNfcAdapter != null) && (!mNfcAdapter.isEnabled())) {
            //show_nfc_message.setText(R.string.tv_nfc_notwork);
        } else if ((mNfcAdapter != null) && (mNfcAdapter.isEnabled())) {
            //show_nfc_message.setText(R.string.tv_nfc_working);
        }
        mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()), 0);
        init_NFC();

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

        socketeBaglan();

        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    mUsbThermalPrinter.start(0);
                    mUsbThermalPrinter.reset();
                } catch (Exception e) {
                    if (e.toString().equals("com.telpo.tps550.api.printer.OverHeatException")) {
                        qrhandler.sendMessage(qrhandler.obtainMessage(OVERHEAT, 1, 0, null));
                    }
                    Log.e("checkstatus()","status error"+" "+e.toString());
                    e.printStackTrace();

                }
            }
        }).start();

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
                        KartSatis.this.runOnUiThread(() -> {
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
                        KartSatis.this.runOnUiThread(() -> {
                            try {
                                myDb.deleteAll("blacklist");

                                String[] parts = myResponse.split(",");
                                for (String uid : parts) {
                                    myDb.blacklistInsert(uid);
                                }

                                Log.i("Güncelleme", "3 - KARALİSTE");


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
            Toast.makeText(KartSatis.this,"Cihaz çevrimdışı modda çalışıyor.",Toast.LENGTH_LONG).show();
        }
    }



    private void initUI() {
        txtKartaYazilacak = (TextView) findViewById(R.id.textView);
        linearLayout = (LinearLayout) findViewById(R.id.myLayout);
        DateTime = (TextView) findViewById(R.id.DateTime);

        // BUTTON
        btn1 = (Button) findViewById(R.id.btn1);
        btn2 = (Button) findViewById(R.id.btn2);
        btn3 = (Button) findViewById(R.id.btn3);
        btn4 = (Button) findViewById(R.id.btn4);
        btn5 = (Button) findViewById(R.id.btn5);
        btn6 = (Button) findViewById(R.id.btn6);
        btn7 = (Button) findViewById(R.id.btn7);
        btn8 = (Button) findViewById(R.id.btn8);
        btn9 = (Button) findViewById(R.id.btn9);
        karta_yukle = (Button) findViewById(R.id.karta_yukle);
        wifi = (ImageView) findViewById(R.id.wifi);

        logo = (ImageView) findViewById(R.id.logo);

        logo.setOnClickListener(v -> {
            startActivity(new Intent(KartSatis.this,Settings.class));
        });

        karta_yukle.setOnClickListener(v -> {
            if(writeMode==4){
                writeMode=0;
                miktar=0;
                txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
                karta_yukle.setText("KARTA YÜKLE");
            }else{
                writeMode=4;
                txtKartaYazilacak.setText("KARTINIZI OKUTUN\n\nMIKTAR : "+Integer.toString(miktar));
                karta_yukle.setText("YÜKLEME İPTAL");
            }
        });

        btn1.setOnClickListener(v -> {
            miktar = 1;
            txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
        });

        btn2.setOnClickListener(v -> {
            miktar = 8;
            txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
        });

        btn3.setOnClickListener(v -> {
            miktar = 15;
            txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
        });

        btn4.setOnClickListener(v -> {
            miktar = 30;
            txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
        });

        btn5.setOnClickListener(v -> {
            miktar = 50;
            txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
        });

        btn6.setOnClickListener(v -> {
            miktar = 75;
            txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
        });

        btn7.setOnClickListener(v -> {
            startActivity(new Intent(KartSatis.this,MusteriEkle.class));
            miktar = 100;
            txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
        });

        btn8.setOnClickListener(v -> {
            startActivity(new Intent(KartSatis.this,MusteriEkle.class));
            miktar = 200;
            txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
        });

        btn9.setOnClickListener(v -> {
            startActivity(new Intent(KartSatis.this,MusteriEkle.class));
            miktar = 300;
            txtKartaYazilacak.setText("YÜKLEMEK İSTEDİĞİNİZ BAKİYEYİ SEÇİN.\nMİKTAR : "+Integer.toString(miktar));
        });

        handler = new Handler();
        final Runnable r = new Runnable() {
            public void run() {
                handler.postDelayed(this, 1000);
            }
        };

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
                    bilet_bas.setEnabled(isWiFi);
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

                handler.postDelayed(this,1000);
            }
        }, 1000);

        writeMode=0;
    }

    public static String[] addX(int n, String[] arr, String x)
    {
        int i;

        // create a new array of size n+1
        String newarr[] = new String[n + 1];

        for (i = 0; i < n; i++)
            newarr[i] = arr[i];

        newarr[n] = x;

        return newarr;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mNfcAdapter != null) {
            mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
            if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(this.getIntent().getAction())) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                Tag detectedTag = this.getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
                try {
                    writeTag(buildNdefMessage(processIntent(this.getIntent())), detectedTag);
                    if(durum==1){
                        MediaPlayer tekli = MediaPlayer.create(this, R.raw.tekli);
                        tekli.start();
                    }else if(durum==-1){
                        MediaPlayer gecersiz = MediaPlayer.create(this, R.raw.gecersiz);
                        gecersiz.start();
                    }else if(durum==2){
                        MediaPlayer yetersiz = MediaPlayer.create(this, R.raw.yetersiz);
                        yetersiz.start();
                    }
                    if(fis_yazdir){
                        Intent receiptInt=new Intent(KartSatis.this,BudgetReceipt.class);
                        receiptInt.putExtra("refId",refId);
                        receiptInt.putExtra("musteri","0");
                        receiptInt.putExtra("key",basilanID);//kart numarası
                        receiptInt.putExtra("last",onceki);//önceki
                        receiptInt.putExtra("next",sonraki);//sonraki
                        startActivity(receiptInt);
                        finish();
                        fis_yazdir=false;
                    }

                    final Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            linearLayout.getBackground().setTint(white);
                            txtKartaYazilacak.setText("Yüklemek istediğiniz bakiyeyi seçin.\nMiktar : 0");
                        }
                    }, 700);
                } catch (InterruptedException | UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try{
            basilanID=ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));
            if (myDb.blacklistController(basilanID)) {
                linearLayout.getBackground().setTint(red);
                txtKartaYazilacak.setText("KARTINIZ KAPATILMIŞTIR!");
                final Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    linearLayout.getBackground().setTint(white);
                    txtKartaYazilacak.setText("Yüklemek istediğiniz bakiyeyi seçin.\nMiktar : 0");
                }, 2000);
            } else if (myDb.tanimliKartController(basilanID)) {
                Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                try {
                    writeTag(buildNdefMessage(processIntent(intent)), detectedTag);

                    if(durum==1){
                        MediaPlayer tekli = MediaPlayer.create(this, R.raw.tekli);
                        tekli.start();
                    }else if(durum==-1){
                        MediaPlayer gecersiz = MediaPlayer.create(this, R.raw.gecersiz);
                        gecersiz.start();
                    }else if(durum==2){
                        MediaPlayer yetersiz = MediaPlayer.create(this, R.raw.yetersiz);
                        yetersiz.start();
                    }

                    if(fis_yazdir){
                        Intent receiptInt=new Intent(KartSatis.this,BudgetReceipt.class);
                        receiptInt.putExtra("refId",refId);
                        receiptInt.putExtra("musteri","0");
                        receiptInt.putExtra("key",basilanID);//kart numarası
                        receiptInt.putExtra("last",onceki);//önceki
                        receiptInt.putExtra("next",sonraki);//sonraki
                        startActivity(receiptInt);
                        finish();
                        fis_yazdir=false;
                    }

                    final Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            linearLayout.getBackground().setTint(white);
                            txtKartaYazilacak.setText("Yüklemek istediğiniz bakiyeyi seçin.\nMiktar : 0");
                        }
                    }, 2000);
                } catch (InterruptedException | UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }else{
                linearLayout.getBackground().setTint(red);
                txtKartaYazilacak.setText("KART SİSTEMDE TANIMLI DEĞİL!");
                final Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    linearLayout.getBackground().setTint(white);
                    txtKartaYazilacak.setText("Yüklemek istediğiniz bakiyeyi seçin.\nMiktar : 0");
                }, 2000);
            }
            Log.w("TAG",basilanID.toString());
        }catch (Exception e){

        }



    }

    private String ByteArrayToHexString(byte [] inarray) {
        int i, j, in;
        String [] hex = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
        String out= "";

        for(j = 0 ; j < inarray.length ; ++j)
        {
            in = (int) inarray[j] & 0xff;
            i = (in >> 4) & 0x0f;
            out += hex[i];
            i = in & 0x0f;
            out += hex[i];
        }
        return out;
    }

    private String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        char[] buffer = new char[2];
        for (int i = 0; i < src.length; i++) {
            buffer[0] = Character.forDigit((src[i] >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(src[i] & 0x0F, 16);
            stringBuilder.append(buffer);
        }
        return stringBuilder.toString();
    }

    public String processIntent(Intent intent) {
        String data = null;
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        String[] techList = tag.getTechList();
        byte[] ID = new byte[20];
        data = tag.toString();
        ID = tag.getId();
        String UID = bytesToHexString(ID);
        String IDString = bytearray2Str(hexStringToBytes(UID.substring(2, UID.length())), 0, 4, 10);
        data += "\n\nUID:\n" + UID;
        data += "\n\nID:\n" + IDString;
        data += "\nData format:";
        for (String tech : techList) {
            data += "\n" + tech;
        }
        /*data += "\nwg26status:-->" + PosUtil.getWg26Status(Long.parseLong(IDString)) + "\n";
        data += "wg34status:-->" + PosUtil.getWg34Status(Long.parseLong(IDString)) + "\n";
        data += "wg32status:-->" + PosUtil.getWg32Status(Long.parseLong(IDString)) + "\n";*/
        //show_nfc_message.setText(data);

        NdefMessage[] msgs = getNdefMessagesFromIntent(intent);
        NdefRecord record = msgs[0].getRecords()[0];
        byte[] payload = record.getPayload();
        String payloadString = new String(payload);
        //txtKartaYazilacak.setText(payloadString);
        return payloadString;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mNfcAdapter != null) {
            stopNFC_Listener();
        }
    }

    private void init_NFC() {
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
    }

    private void stopNFC_Listener() {
        mNfcAdapter.disableForegroundDispatch(this);
    }


    private static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    private static String bytearray2Str(byte[] data, int start, int length, int targetLength) {
        long number = 0;
        if (data.length < start + length) {
            return "";
        }
        for (int i = 1; i <= length; i++) {
            number *= 0x100;
            number += (data[start + length - i] & 0xFF);
        }
        return String.format("%0" + targetLength + "d", number);
    }

    void writeTag(NdefMessage message, Tag tag)
    {
        int size = message.toByteArray().length;

        try
        {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null)
            {
                ndef.connect();

                if (!ndef.isWritable())
                {
                    Toast.makeText(KartSatis.this, "Bu kart yazılabilir değil.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (ndef.getMaxSize() < size)
                {
                    Toast.makeText(KartSatis.this,
                            "Kart boyutu desteklenmiyor", Toast.LENGTH_LONG).show();
                    return;
                }

                ndef.writeNdefMessage(message);
                return;
            }else {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        format.format(message);
                        return;
                    } catch (IOException e) {
                        return;
                    }
                } else {
                    return;
                }
            }
        }
        catch (Exception e)
        {

        }

    }

    @SuppressLint("WrongConstant")
    private NdefMessage buildNdefMessage(String parsedData) throws InterruptedException, UnsupportedEncodingException {
        NdefMessage message = null;
        try{
            String data = basilanID+",GECERSIZ,0";
            String cozulmus = decrypt(parsedData);
            Log.i("KART",cozulmus);

            if(writeMode==2) {
                String[] income = cozulmus.split(",");
                data = basilanID+",KOOP2021,0,0";
                linearLayout.getBackground().setTint(green);
                txtKartaYazilacak.setText("\nKART SIFIRLANDI");
                durum=0;
            }else if(writeMode==4) {
                refId = UUID.randomUUID().toString().split("-")[4];
                try{
                    String[] income = cozulmus.split(",");
                    String yuklenecek = Integer.toString(Integer.parseInt(income[2])+miktar);
                    onceki = income[2];
                    sonraki = yuklenecek;
                    if(income[3].equals("0")){
                        onceki = "0";
                        sonraki = Integer.toString(miktar);

                        data = basilanID + ",KOOP2021,"+Integer.toString(miktar)+","+kart_tipi;
                        linearLayout.getBackground().setTint(green);
                        txtKartaYazilacak.setText("YÜKLEME BAŞARILI\nYÜKLENEN : "+Integer.toString(miktar));

                        try{
                            JSONObject jsonObject = new JSONObject();
                            try {
                                jsonObject.put("refId",refId);
                                jsonObject.put("uid", basilanID);
                                jsonObject.put("tip",kart_tipi.toUpperCase());
                                jsonObject.put("onceki", "0");
                                jsonObject.put("sonraki", Integer.toString(miktar));
                                jsonObject.put("tarih", currentDateandTime);
                                jsonObject.put("istasyon", istasyon_id);
                                jsonObject.put("satis","1");
                            } catch (JSONException d) {
                                d.printStackTrace();
                            }

                            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                            RequestBody body = RequestBody.create(JSON, jsonObject.toString());

                            OkHttpClient client = new OkHttpClient();
                            String url = ipAdresi+":"+apiPort+"/api/newdolum/";
                            Request request = new Request.Builder()
                                    .url(url)
                                    .post(body)
                                    .build();
                            client.newCall(request).enqueue(new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    // OFFLINE DOLUM KAYIT
                                }
                                @Override
                                public void onResponse(@NonNull Call call, @NonNull Response response) {

                                }
                            });
                        }catch (Exception f){
                            Log.w("Güncelleme",f);
                        }

                        miktar = 0;
                        writeMode = 0;
                        durum = 0;
                        fis_yazdir=true;
                    }else{
                        data = basilanID + ",KOOP2021,"+yuklenecek+","+kart_tipi;
                        linearLayout.getBackground().setTint(green);
                        txtKartaYazilacak.setText("YÜKLEME BAŞARILI\nYÜKLENEN : "+yuklenecek);

                        try{
                            JSONObject jsonObject = new JSONObject();
                            try {
                                jsonObject.put("refId",refId);
                                jsonObject.put("uid", basilanID);
                                jsonObject.put("tip",kart_tipi.toUpperCase());
                                jsonObject.put("onceki", income[2]);
                                jsonObject.put("sonraki", yuklenecek);
                                jsonObject.put("tarih", currentDateandTime);
                                jsonObject.put("istasyon", istasyon_id);
                                jsonObject.put("satis","0");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                            RequestBody body = RequestBody.create(JSON, jsonObject.toString());

                            OkHttpClient client = new OkHttpClient();
                            String url = ipAdresi+":"+apiPort+"/api/newdolum/";
                            Request request = new Request.Builder()
                                    .url(url)
                                    .post(body)
                                    .build();
                            client.newCall(request).enqueue(new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    // OFFLINE DOLUM KAYIT
                                }
                                @Override
                                public void onResponse(@NonNull Call call, @NonNull Response response) {

                                }
                            });
                        }catch (Exception e){
                            Log.w("Güncelleme",e);
                        }
                        miktar = 0;
                        writeMode = 0;
                        durum = 0;
                        fis_yazdir=true;
                    }

                }catch (Exception e){

                }
            }else{
                String[] income = cozulmus.split(",");
                String yuklenecek = Integer.toString(Integer.parseInt(income[2])+miktar);
                txtKartaYazilacak.setText("\nMevcut Bakiye : "+income[2]);
                data=cozulmus;
                durum=0;
            }

            //String data = txtKartaYazilacak.getText().toString().trim();
            String yazilacak = encrypt(data);
            String mimeType = "application/com.telpo.davraz";

            byte[] mimeBytes = mimeType.getBytes(Charset.forName("UTF-8"));
            byte[] dataBytes = yazilacak.getBytes(Charset.forName("UTF-8"));
            byte[] id = new byte[0];

            NdefRecord record = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeBytes, id, dataBytes);
            message = new NdefMessage(new NdefRecord[]{record});

            return message;
        }catch (Exception e) {
            durum = -1;
            txtKartaYazilacak.setText("GEÇERSİZ KART");
            linearLayout.getBackground().setTint(red);

            byte[] id = new byte[0];
            byte[] dataBytes = (basilanID+",KOOP2021,0,0").getBytes(Charset.forName("UTF-8"));
            byte[] mimeBytes = "application/com.telpo.davraz".getBytes(Charset.forName("UTF-8"));
            NdefRecord record = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeBytes, id, dataBytes);
            message = new NdefMessage(new NdefRecord[]{record});
            return message ;
        }
    }

    NdefMessage[] getNdefMessagesFromIntent(Intent intent)
    {
        // Parse the intent
        NdefMessage[] msgs = null;
        String action = intent.getAction();
        if (action.equals(NfcAdapter.ACTION_TAG_DISCOVERED) || action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED))
        {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null)
            {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++)
                {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }

            }
            else
            {
                // Unknown tag type
                byte[] empty = new byte[]{};
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty);
                NdefMessage msg = new NdefMessage(new NdefRecord[]{record});
                msgs = new NdefMessage[]{msg};
            }

        }
        return msgs;
    }

    public static String encrypt(String value) {
        String key = "aesEncryptionKey";
        String initVector = "encryptionIntVec";
        try {
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] encrypted = cipher.doFinal(value.getBytes());
            byte[] encode = Base64.encode(encrypted,Base64.NO_WRAP);
            return new String(encode);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
    public static String decrypt(String value) {
        String key = "aesEncryptionKey";
        String initVector = "encryptionIntVec";
        try {
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
            byte[] original = cipher.doFinal(Base64.decode(value,Base64.NO_WRAP));

            return new String(original);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onDestroy() {
        if (progressDialog != null && !KartSatis.this.isFinishing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        unregisterReceiver(printReceive);
        mUsbThermalPrinter.stop();
        super.onDestroy();
    }

    private class QrHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case NOPAPER:
                    noPaperDlg();
                    break;
                case LOWBATTERY:
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(KartSatis.this);
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
                    new KartSatis.qrcodePrintThread().start();
                    break;
                case OVERHEAT:
                    AlertDialog.Builder overHeatDialog = new AlertDialog.Builder(KartSatis.this);
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
                    if (progressDialog != null && !KartSatis.this.isFinishing()) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                    break;
                default:
                    Toast.makeText(KartSatis.this, "Print Error!", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
    private void noPaperDlg() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(KartSatis.this);
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
}
