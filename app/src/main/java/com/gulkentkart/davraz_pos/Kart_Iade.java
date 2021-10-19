package com.gulkentkart.davraz_pos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Kart_Iade extends Activity {

    private NfcAdapter mNfcAdapter;
    private PendingIntent mPendingIntent;
    private String basilanID;

    private Database myDb;
    private String ipAdresi,socketPort,apiPort,istasyon_id;
    private String refId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_kart_iade);

        myDb = new Database(this);

        ipAdresi   = "http://"+myDb.ayarGetir("sunucuIP");
        socketPort = myDb.ayarGetir("socketPort");
        apiPort   = myDb.ayarGetir("apiPort");

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
    }

    private void init_NFC() {
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
    }

    private void stopNFC_Listener() {
        mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mNfcAdapter != null) {
            stopNFC_Listener();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mNfcAdapter != null) {
            mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
            if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(this.getIntent().getAction())) {
                Tag detectedTag = this.getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
                try {
                    writeTag(buildNdefMessage(processIntent(this.getIntent())),
                            detectedTag);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
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
            Log.w("TAG",basilanID.toString());
        }catch (Exception e){

        }

        Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        try {
            writeTag(buildNdefMessage(processIntent(intent)), detectedTag);
        } catch (InterruptedException | UnsupportedEncodingException e) {
            e.printStackTrace();
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
        return payloadString;
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            startActivity(new Intent(Kart_Iade.this,KartTipi.class));
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    void writeTag(NdefMessage message, Tag tag) {
        int size = message.toByteArray().length;

        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    Toast.makeText(Kart_Iade.this, "Bu kart yazılabilir değil.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (ndef.getMaxSize() < size) {
                    Toast.makeText(Kart_Iade.this,
                            "Kart boyutu desteklenmiyor", Toast.LENGTH_LONG).show();
                    return;
                }
                ndef.writeNdefMessage(message);
            }

        } catch (FormatException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("WrongConstant")
    private NdefMessage buildNdefMessage(String parsedData) throws InterruptedException, UnsupportedEncodingException {
        NdefMessage message = null;

        try{
            JSONObject jsonObject = new JSONObject();
            try {
                refId = UUID.randomUUID().toString().split("-")[4];
                String currentDateandTime = new SimpleDateFormat("dd-MM-yyyy\nHH:mm:ss").format(new Date());
                jsonObject.put("refId",refId);
                jsonObject.put("uid", basilanID);
                jsonObject.put("tip","");
                jsonObject.put("onceki", "0");
                jsonObject.put("sonraki", "0");
                jsonObject.put("tarih", currentDateandTime);
                jsonObject.put("istasyon", istasyon_id);
                jsonObject.put("satis","2");
            } catch (JSONException d) {
                d.printStackTrace();
            }

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, jsonObject.toString());

            OkHttpClient client = new OkHttpClient();
            String url = ipAdresi+":"+apiPort+"/api/newiade/";
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
            Log.w("Kart Sıfırlama",f);
        }

        String data = basilanID+",KOOP2021,0,0";
        String yazilacak = encrypt(data);
        String mimeType = "application/com.telpo.davraz";
        byte[] mimeBytes = mimeType.getBytes(Charset.forName("UTF-8"));
        byte[] dataBytes = yazilacak.getBytes(Charset.forName("UTF-8"));
        byte[] id = new byte[0];
        NdefRecord record = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeBytes, id, dataBytes);
        message = new NdefMessage(new NdefRecord[]{record});
        return message;
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
}
