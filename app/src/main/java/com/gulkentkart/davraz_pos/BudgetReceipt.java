package com.gulkentkart.davraz_pos;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textview.MaterialTextView;
import com.telpo.tps550.api.printer.ThermalPrinter;
import com.telpo.tps550.api.printer.UsbThermalPrinter;
import com.telpo.tps550.api.util.StringUtil;
import com.telpo.tps550.api.util.SystemUtil;

import java.text.SimpleDateFormat;
import java.util.Date;

public class BudgetReceipt extends AppCompatActivity {

    /**
     * fiş yazdırma
     */

    public static String printVersion;
    private final int NOPAPER = 3;
    private final int LOWBATTERY = 4;
    private final int PRINTVERSION = 5;
    private final int PRINTCONTENT = 9;
    private final int CANCELPROMPT = 10;
    private final int OVERHEAT = 12;
    private final int PRINTERR = 11;
    private int printGray=4;private String Result;
    private Boolean nopaper = false;
    private boolean LowBattery = false;
    private ProgressDialog progressDialog;
    public static String printContent=null;
    private int leftDistance =10;
    private int lineDistance=10;
    private int wordFont=3;
    public String data=null;
    public String last=null;
    public String current=null;
    private String kartSahibi=null;
    private String musteri=null;
    private String refId;

    ProgressDialog dialog;
    MyHandler handler;
    UsbThermalPrinter mUsbThermalPrinter = new UsbThermalPrinter(BudgetReceipt.this);
    MaterialTextView bayi,gun,saat;
    Button fiss;
    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case NOPAPER:
                    noPaperDlg();
                    break;
                case LOWBATTERY:
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(BudgetReceipt.this);
                    alertDialog.setTitle(R.string.operation_result);
                    alertDialog.setMessage(getString(R.string.LowBattery));
                    alertDialog.setPositiveButton(getString(R.string.dialog_comfirm), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    });
                    alertDialog.show();
                    break;

                case PRINTCONTENT:
                    new textPrintThread().start();
                    break;
                case OVERHEAT:
                    AlertDialog.Builder overHeatDialog = new AlertDialog.Builder(BudgetReceipt.this);
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
                    if (progressDialog != null && !BudgetReceipt.this.isFinishing()) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                    break;
                default:
                    Toast.makeText(BudgetReceipt.this, "Print Error!", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
    private void noPaperDlg() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(BudgetReceipt.this);
        dlg.setTitle(getString(R.string.noPaper));
        dlg.setMessage(getString(R.string.noPaperNotice));
        dlg.setCancelable(false);
        dlg.setPositiveButton(R.string.sure, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ThermalPrinter.stop(BudgetReceipt.this);
            }
        });
        dlg.show();
    }
    private class textPrintThread extends Thread {
        @Override
        public void run() {
            super.run();
            try{
                if(musteri=="1"){
                    //ThermalPrinter.start(RaporActivity.this);
                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(ThermalPrinter.ALGIN_LEFT);
                    mUsbThermalPrinter.setLeftIndent(leftDistance);
                    mUsbThermalPrinter.setLineSpace(lineDistance);
                    mUsbThermalPrinter.setFontSize(2);
                    mUsbThermalPrinter.setGray(printGray);
                    mUsbThermalPrinter.addString("Davraz Kart\n");
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy \nHH:mm:ss \n");
                    String mDate = sdf.format(new Date());
                    mUsbThermalPrinter.addString(mDate);
                    mUsbThermalPrinter.addString("Kart sahibi: "+kartSahibi);
                    mUsbThermalPrinter.addString("Kart Numarası: "+printContent);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(20);
                }else{
                    //ThermalPrinter.start(RaporActivity.this);
                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(ThermalPrinter.ALGIN_LEFT);
                    mUsbThermalPrinter.setLeftIndent(leftDistance);
                    mUsbThermalPrinter.setLineSpace(lineDistance);
                    mUsbThermalPrinter.setFontSize(2);
                    mUsbThermalPrinter.setGray(printGray);
                    mUsbThermalPrinter.addString("Davraz Kart\n");
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy \nHH:mm:ss");
                    String mDate = sdf.format(new Date());
                    mUsbThermalPrinter.addString(mDate);
                    mUsbThermalPrinter.addString("FişID: "+refId);
                    mUsbThermalPrinter.addString("Kart Numarası: "+printContent);
                    mUsbThermalPrinter.addString("Önceki Bakiye: "+last);
                    mUsbThermalPrinter.addString("Sonraki Bakiye: "+current);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(20);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Result=e.toString();
                if (Result.equals("com.telpo.tps550.api.printer.NoPaperException")) {
                    nopaper = true;
                } else if (Result.equals("com.telpo.tps550.api.printer.OverHeatException")) {
                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                } else {
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }
            }finally {
                handler.sendMessage(handler.obtainMessage(CANCELPROMPT,1,0,null));
                if(nopaper){
                    handler.sendMessage(handler.obtainMessage(NOPAPER,1,0,null));
                    nopaper=false;
                    return;
                }
                //ThermalPrinter.stop(RaporActivity.this);
            }
        }
    }
    private final BroadcastReceiver printReceive = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)){
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_NOT_CHARGING);
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE,0);
                if(SystemUtil.getDeviceType()== StringUtil.DeviceModelEnum.TPS390.ordinal()){
                    if(level * 5 <= scale){
                        LowBattery =true;
                    } else {
                        LowBattery=false;
                    }
                }else {
                    if (status != BatteryManager.BATTERY_STATUS_CHARGING){
                        if(level * 5 <= scale){
                            LowBattery = true;
                        } else{
                            LowBattery =false;
                        }
                    }else {
                        LowBattery=false;
                    }
                }
            }
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
    protected void onDestroy() {
        if (progressDialog != null && !BudgetReceipt.this.isFinishing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        unregisterReceiver(printReceive);
        mUsbThermalPrinter.stop();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            startActivity(new Intent(BudgetReceipt.this,KartTipi.class));
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Bu kısma kadar fiş için
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_budget_receipt);

        handler =new MyHandler();
        IntentFilter pIntentFilter = new IntentFilter();
        pIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        pIntentFilter.addAction("android.intent.action.BATTERY_CAPACITY_EVENT");
        registerReceiver(printReceive, pIntentFilter);

        Intent intent=getIntent();
        refId = intent.getStringExtra("refId");
        musteri=intent.getStringExtra("musteri");
        kartSahibi = intent.getStringExtra("adSoyad");
        data=intent.getStringExtra("key");
        last=intent.getStringExtra("last");
        current=intent.getStringExtra("next");

        fiss=findViewById(R.id.printReceipt);
        fiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printContent=data;
                if(printContent ==null || printContent.length()==0) {
                    Toast.makeText(getApplicationContext(), getString(R.string.empty), Toast.LENGTH_LONG).show();
                    return;
                }
                if (LowBattery==true){
                    handler.sendMessage(handler.obtainMessage(LOWBATTERY,1,0,null));
                }else{
                    if (!nopaper){
                        progressDialog = ProgressDialog.show(BudgetReceipt.this,getString(R.string.bl_dy),getString(R.string.printing_wait));

                        handler.sendMessage(handler.obtainMessage(PRINTCONTENT,1,0,null));
                        startActivity(new Intent(BudgetReceipt.this,KartTipi.class));
                        finish();
                    }else {
                        Toast.makeText(BudgetReceipt.this,getString(R.string.printInit),Toast.LENGTH_LONG).show();
                    }
                }
            }
        });



        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    mUsbThermalPrinter.start(0);
                    mUsbThermalPrinter.reset();
                } catch (Exception e) {
                    if (e.toString().equals("com.telpo.tps550.api.printer.OverHeatException")) {
                        handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                    }
                    Log.e("checkstatus()","status error"+" "+e.toString());
                    e.printStackTrace();

                }
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

}