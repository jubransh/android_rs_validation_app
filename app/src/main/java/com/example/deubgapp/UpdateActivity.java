package com.example.deubgapp;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.Selection;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.intel.realsense.librealsense.DebugProtocol;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.DeviceListener;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.ProgressListener;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.Updatable;
import com.intel.realsense.librealsense.UpdateDevice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class UpdateActivity extends AppCompatActivity {
    private boolean resetStarted = false;
    private boolean resetDone = false;

    private ProgressBar mFWUpdateProgressBar;
    public Button dumpBtn, vanillaBtn, eraseBtn, writeBtn, rstBtn;
    public TextView statusTV, mFWUpdateProgressTV;
    public Device mDevice;
    RsContext mRsContext;
    DeviceList devices;

    String TAG = "DebugApp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);

        //Init Device ******************************************
        RsContext.init(getApplicationContext());
        mRsContext = new RsContext();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        initDevice();
        mRsContext.setDevicesChangedCallback(new DeviceListener() {
            @Override
            public void onDeviceAttach() {
                if(resetStarted){
                    resetDone = true;
                    resetStarted = false;
                }
                appendTerminal("Device Attached");
                initDevice();
            }

            @Override
            public void onDeviceDetach() {
                Toast.makeText(getApplicationContext(), "Device Detached", Toast.LENGTH_LONG).show();
                appendTerminal("Device Detached");
                devices = mRsContext.queryDevices();
            }
        });

        //*******************************************************

        statusTV = findViewById(R.id.status_text_view);
        mFWUpdateProgressBar = findViewById(R.id.update_progress_bar);
        mFWUpdateProgressTV = findViewById(R.id.progress_percentage_text_view);

        dumpBtn = findViewById(R.id.btn_dump);
        vanillaBtn = findViewById(R.id.btn_vanilla);
        eraseBtn = findViewById(R.id.btn_erase);
        writeBtn = findViewById(R.id.btn_write);
        rstBtn = findViewById(R.id.btn_rst);

        dumpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String pathToSave = "/DebugApp/dump.bin";
                dumpFlash(pathToSave).start();
            }
        });

        vanillaBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread vanillaThread = updateVanilla("/DebugApp/Signed_Image_UVC_15_4_0_0.bin");
                vanillaThread.start();
            }
        });

        eraseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread eraseThread = eraseFlashRW();
                eraseThread.start();
            }
        });

        writeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String pathToSave = "/DebugApp/dump.bin";
                try {
                    Thread writeThread = writeRWImage(pathToSave);
                    writeThread.start();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        rstBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread resetThread = resetCamera();
                resetThread.start();
            }
        });
    }

    //===================================== Public Methods =========================================
    public Thread dumpFlash(final String filePath) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                String whereToSave = Environment.getExternalStorageDirectory().getAbsolutePath() + filePath;
                //Carete Dir if not exist
                File f = new File(whereToSave);
                File parentDir = new File(f.getParent());
                if (!parentDir.exists()) {
                    parentDir.mkdir();
                }

                byte opcode[] = new byte[] {(byte)0x09, (byte)0x0, (byte)0x0, (byte)0x0};//FRB = 0x09
                byte p3[] = new byte[] {(byte)0x00, (byte)0x0, (byte)0x0, (byte)0x0};
                byte p4[] = new byte[] {(byte)0x00, (byte)0x0, (byte)0x0, (byte)0x0};
                byte data[] = new byte[] {};

                byte flashDump[] = new byte[1536 * 1024];
                int rwSize = flashDump.length;
                int segmentSize = 1000;
                int bulks = rwSize / segmentSize;
                int restBytesSize = rwSize % segmentSize;
                bulks += restBytesSize == 0 ? 0 : 1;

                appendTerminal("Reading bin data from Device ....");
                for(int i=0; i<bulks; i++){
                    int startIndex = i * segmentSize;

                    int actualDataSize = segmentSize;
                    if(restBytesSize > 0 && i == bulks -1) //Last Bulk which could be partial
                        actualDataSize = restBytesSize;

                    byte p1[] = reverseBytes(ByteBuffer.allocate(4).putInt(startIndex).array(), 4);
                    byte p2[] = reverseBytes(ByteBuffer.allocate(4).putInt(segmentSize).array(), 4);

                    byte[] buff = sendCommand(opcode, p1, p2, p3, p4, data);
                    System.arraycopy(buff, 0, flashDump, startIndex, actualDataSize);
                    updateProgress((i+1.0)/bulks);
                }

                //Save the read buffer to bin file
                try {
                    // create a writer
                    FileOutputStream fos = null;
                    fos = new FileOutputStream(new File(whereToSave));

                    // write data to file
                    appendTerminal("Saving bin Data to the Internal Storage ....");
                    for(int i=0; i<flashDump.length; i++){
                        fos.write(flashDump[i]);
                        updateProgress((i+1.0)/flashDump.length);
                    }

                    // close the writer
                    fos.close();

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        };
         return thread;
    }

    public Thread updateVanilla(String vanillaPath) {
        if (mDevice == null) {
            Toast("No device is connected");
            return null;
        }
        vanillaPath = Environment.getExternalStorageDirectory().getAbsolutePath() + vanillaPath;
        return updateSignedThread(vanillaPath);
    }

    public Thread eraseFlashRW() {
        Thread eraseThread = new Thread() {
            @Override
            public void run() {
                appendTerminal("Erasing Flash RW Area Started");

                //Erase First 24 blocks (which is 1536KB = 24 * 64KB)
                byte opcode[] = new byte[] {(byte)0x16, (byte)0x0, (byte)0x0, (byte)0x0};//FEB = 0x16
                byte p1[] = new byte[] {(byte)0x00, (byte)0x0, (byte)0x0, (byte)0x0};//Starting from Index 0
                byte p2[] = new byte[] {(byte)0x18, (byte)0x0, (byte)0x0, (byte)0x0};// 24 Blocks = 0x18
                byte p3[] = new byte[] {(byte)0x00, (byte)0x0, (byte)0x0, (byte)0x0};
                byte p4[] = new byte[] {(byte)0x00, (byte)0x0, (byte)0x0, (byte)0x0};
                byte data[] = new byte[] {};

                byte[] returnedValue = sendCommand(opcode, p1, p2, p3, p4, data);
                if (returnedValue ==null)
                    appendTerminal("Erasing Flash RW Area was failed");
                else
                    appendTerminal("Erasing Flash RW Area Done");
            }
        };
        return eraseThread;
    }

    public Thread writeRWImage(final String path) throws Exception {

        Thread burnThread = new Thread() {
            @Override
            public void run() {
                appendTerminal("Starting burring process flash RW area ....");
                String mergeImagePath = Environment.getExternalStorageDirectory().getAbsolutePath() + path;
                byte[] bytesToWrite = null;
                try {
                    bytesToWrite = readFwFile(new FileInputStream(new File(mergeImagePath)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(bytesToWrite == null){
                    appendTerminal("Cannot read the merge flash image " + mergeImagePath);
                    return;
                }

                int expectedSizeInBytes = 1536 * 1024;
                if (bytesToWrite.length != expectedSizeInBytes)
                    try {
                        throw new Exception("Illegal Image Size");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                byte opcode[] = new byte[] {(byte)0x0A, (byte)0x0, (byte)0x0, (byte)0x0};//FWB = 0x0A
                byte p3[] = new byte[] {(byte)0x00, (byte)0x0, (byte)0x0, (byte)0x0};
                byte p4[] = new byte[] {(byte)0x00, (byte)0x0, (byte)0x0, (byte)0x0};

                int segmentSize = 1000;

                int bulks = expectedSizeInBytes / segmentSize;
                int restBytesSize = expectedSizeInBytes % segmentSize;
                bulks += restBytesSize == 0 ? 0 : 1;

                for(int i=0; i<bulks; i++){
                    int startIndex = i * segmentSize;
                    byte p1[] = reverseBytes(ByteBuffer.allocate(4).putInt(startIndex).array(), 4); // Offset

                    int actualDataSize = segmentSize;
                    if(restBytesSize > 0 && i == bulks -1) //Last Bulk which could be partial
                        actualDataSize = restBytesSize;

                    byte p2[] = reverseBytes(ByteBuffer.allocate(4).putInt(actualDataSize).array(), 4);//Size
                    byte data[] = new byte[actualDataSize];
                    System.arraycopy(bytesToWrite, startIndex, data, 0, actualDataSize);

                    byte[] buff = sendCommand(opcode, p1, p2, p3, p4, data);

                    updateProgress((i+1.0)/bulks);
                }
                appendTerminal("Burring process Done");
            }
        };
        return burnThread;
    }

    public Thread resetCamera() {
        resetStarted = true;
        Thread resetThread = new Thread() {
            @Override
            public void run() {
                appendTerminal("Resetting Device");
                mDevice.hardwareReset();
                appendTerminal("Waiting For Device to be attached again");
                while (!resetDone){
                    //Do nothing
                }
            }
        };
        return resetThread;
    }

    //==================================== Helping Methods =========================================

    private void appendTerminal(final String str){
        runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                String currentText = statusTV.getText() + " ";
                statusTV.setText(currentText + str + "\n");

                int position = statusTV.length();
                Editable etext = (Editable) statusTV.getText();
                Selection.setSelection(etext, position);
            }
        });
    }

    private void initDevice(){
        devices = mRsContext.queryDevices();
        if(devices.getDeviceCount() > 0){
            mDevice = devices.createDevice(0);
        }
    }

    public byte[] sendCommand(byte[] opcode, byte[] p1, byte[] p2, byte[] p3, byte[] p4, byte[] data) {
        int sizeInt = data.length + 0x14;
        byte[] size = ByteBuffer.allocate(4).putInt(sizeInt).array();
        size = reverseBytes(size, 2);
        byte magicNumber[] = new byte[]{(byte) 0xab, (byte) 0xcd};

        //Merge bytes
        byte[] fullCommandBytes = new byte[size.length + magicNumber.length + opcode.length + p1.length + p2.length + p3.length + p4.length + data.length];
        System.arraycopy(size, 0, fullCommandBytes, 0, size.length);
        System.arraycopy(magicNumber, 0, fullCommandBytes, 2, magicNumber.length);
        System.arraycopy(opcode, 0, fullCommandBytes, 4, opcode.length);
        System.arraycopy(p1, 0, fullCommandBytes, 8, p1.length);
        System.arraycopy(p2, 0, fullCommandBytes, 12, p2.length);
        System.arraycopy(p3, 0, fullCommandBytes, 16, p3.length);
        System.arraycopy(p4, 0, fullCommandBytes, 20, p4.length);
        System.arraycopy(data, 0, fullCommandBytes, 24, data.length);

        if (!mDevice.is(Extension.DEBUG)) {
            throw new RuntimeException("Device cannot enter DebugProtocol state");
        }

        DebugProtocol dev = mDevice.as(Extension.DEBUG);
        byte[] result = dev.SendAndReceiveRawData(fullCommandBytes);

        byte[] returnCodeBytes = new byte[4];
        byte[] rawDataBytes = new byte[result.length - returnCodeBytes.length];

        System.arraycopy(result, 0, returnCodeBytes, 0, 4);
        System.arraycopy(result, 4, rawDataBytes, 0, result.length - 4);

        if(Arrays.equals(returnCodeBytes, opcode))
            return rawDataBytes;

        return null;
    }

    private  byte[] reverseBytes(byte[] bytes, int trimIndex){
        byte[] reversed = new byte[trimIndex];
        for(int i=0; i< trimIndex; i++){
            reversed[i] = bytes[bytes.length - 1 - i];
        }
        return reversed;
    }

    public Thread updateSignedThread(final String FWPath) {
        if (!mDevice.is(Extension.UPDATABLE) && !mDevice.is(Extension.UPDATE_DEVICE)) {
            throw new RuntimeException("Device is not updatable");
        }

        return new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "updateSignedThread()");
                if (!mDevice.is(Extension.UPDATE_DEVICE)) {
                    final Updatable u = mDevice.as(Extension.UPDATABLE);
                    Log.d(TAG, "Entering DFU...");
                    appendTerminal("Entering DFU");
                    u.enterUpdateState();

                    try {
                        // Allow time for detach event
                        Thread.sleep(500);

                        Log.d(TAG, "Waiting for device to attach...");
                        while (mDevice == null) {
                            // wait for attach event
                            Thread.sleep(200);
                        }
                        Log.d(TAG, "Wait some more just in case");
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.e(TAG, Log.getStackTraceString(e));
                    }


                    if (!mDevice.is(Extension.UPDATE_DEVICE)) {
                        throw new RuntimeException("Failed to enter DFU");
                    }
                }
                Log.d(TAG, "Device in DFU");
                appendTerminal("Device in DFU");

                UpdateDevice ud = mDevice.as(Extension.UPDATE_DEVICE);
                try {
                    final byte[] bytes = readFwFile(new FileInputStream(new File(FWPath)));
                    ud.update(bytes, new FWUpdatePL());
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, Log.getStackTraceString(e));
                    throw new RuntimeException(e);
                }
                appendTerminal("Updating Vanilla Done");
                Log.i(TAG, "Signed FW Update has finished!");
            }
        });
    }

    public static byte[] readFwFile(InputStream in) throws IOException {
        int length = in.available();
        byte[] rv = new byte[length];
        int len = in.read(rv,0, rv.length);
        in.close();
        return rv;
    }

    public void Toast(final String message){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message,
                        Toast.LENGTH_SHORT).show();
            }});
    }
    //===================================== Inner Classes ==========================================
    private void updateProgress(double p){
        final int progress = (int)(p * 100);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFWUpdateProgressBar.setProgress(progress);
                mFWUpdateProgressTV.setText(progress + "%");
            }
        });
    }

    class FWUpdatePL implements ProgressListener {
        private long last = System.currentTimeMillis();
        private int updateRateMs = 100;

        FWUpdatePL() {
            super();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFWUpdateProgressBar.setProgress(0);
                    mFWUpdateProgressTV.setText("0%");
                }
            });

        }

        @Override
        public void onProgress(float v) {
            if ((System.currentTimeMillis() - last) > updateRateMs) {
                final int progress = (int)(v * 100);
                Log.d(TAG, "Updating FW... " + progress + "%");
                last = System.currentTimeMillis();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mFWUpdateProgressBar.setProgress(progress);
                        mFWUpdateProgressTV.setText(progress + "%");
                    }
                });
            }
        }
    }

}