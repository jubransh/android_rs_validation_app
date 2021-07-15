package com.example.deubgapp;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.Selection;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.intel.realsense.librealsense.CameraInfo;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.DepthFrame;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.DeviceListener;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.Frame;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.PipelineProfile;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.StreamType;
import com.intel.realsense.librealsense.VideoFrame;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ManualModeActivity extends AppCompatActivity {

    TextView camNameTV, camSerialTV, cameraFWTV, terminal;
    TextView depthCntTV, irCntTV, colorCntTV, depthTsTV, irTsTV, colorTsTV, depthDropsTV, irDropsTV, colorDropsTV;
    Spinner profilesSpinner;
    Button start, stop, clear;

    RsContext mRsContext;
    DeviceList devices;
    Device selectedDevice;
    boolean mIsStreaming;
    private Thread mStreamingThread;
    ProfileGenerator pG;
    String selectedProfile;
    Date startTime = null;
    Date firstFrameTime = null;
    long timeToFirstFrame;
    double lastColorTS = 0;
    double lastDepthTS = 0;
    double lastIrTS = 0;
    int depthDrops = 0;
    int colorDrops = 0;
    int irDrops = 0;
    Config cfg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_mode);

        pG = new ProfileGenerator();
        mIsStreaming = false;

        //Build the view objects
        camNameTV = findViewById(R.id.cameraName);
        camSerialTV = findViewById(R.id.camerSerial);
        cameraFWTV = findViewById(R.id.cameraFW);
        terminal = findViewById(R.id.terminal);
        profilesSpinner = findViewById(R.id.profile_drop_down);
        depthCntTV = findViewById(R.id.depth_id);
        irCntTV = findViewById(R.id.ir_id);
        colorCntTV = findViewById(R.id.color_id);
        depthTsTV = findViewById(R.id.depth_ts);
        irTsTV = findViewById(R.id.ir_ts);
        colorTsTV = findViewById(R.id.color_ts);
        depthDropsTV = findViewById(R.id.depth_drops);
        irDropsTV = findViewById(R.id.ir_drops);
        colorDropsTV = findViewById(R.id.color_drops);

        start = findViewById(R.id.btn_start);
        stop = findViewById(R.id.btn_stop);
        clear = findViewById(R.id.btn_clear);

        RsContext.init(getApplicationContext());
        mRsContext = new RsContext();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        getVersionsData();

        terminal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideKeyboard(ManualModeActivity.this);
            }
        });

        mRsContext.setDevicesChangedCallback(new DeviceListener() {
            @Override
            public void onDeviceAttach() {
                appendTerminal("Device Attached");
                getVersionsData();
            }


            @Override
            public void onDeviceDetach() {
                Toast.makeText(getApplicationContext(), "Device Detached", Toast.LENGTH_LONG).show();
                appendTerminal("Device Detached");
                devices = mRsContext.queryDevices();
                camNameTV.setText("");
                camSerialTV.setText("");
                cameraFWTV.setText("");

                //Clear Spinner
                profilesSpinner.setAdapter(null);
            }
        });

        profilesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectedProfile = profilesSpinner.getAdapter().getItem(i).toString();
                Toast.makeText(getApplicationContext(), selectedProfile, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                appendTerminal("Starting "+ selectedProfile);
                startTime = Calendar.getInstance().getTime();
                firstFrameTime = null;
                timeToFirstFrame = -1;
                cfg = pG.parseConfig(selectedProfile);
                try {
                    depthCntTV.setText("");
                    irCntTV.setText("");
                    colorCntTV.setText("");
                    depthTsTV.setText("");
                    irTsTV.setText("");
                    colorTsTV.setText("");
                    depthDropsTV.setText("");
                    irDropsTV.setText("");
                    colorDropsTV.setText("");
                    startStream(cfg);
                } catch (Exception e) {
                    e.printStackTrace();
                    appendTerminal("Starting streaming Filed on: " + e.getMessage());
                }
            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    appendTerminal("Stopping Streaming");
                    lastColorTS = 0;
                    lastDepthTS = 0;
                    lastDepthTS = 0;
                    colorDrops = 0;
                    depthDrops = 0;
                    irDrops = 0;
                    stopStreaming();
                    if(cfg != null)
                        cfg.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    appendTerminal("Stopping streaming Filed on: " + e.getMessage());
                }
            }
        });

        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        terminal.setText("");
                    }
                });
            }
        });
    }

    private void appendTerminal(final String str){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String currentText = terminal.getText() + " ";
                terminal.setText(currentText + str + "\n");

                int position = terminal.length();
                Editable etext = (Editable) terminal.getText();
                Selection.setSelection(etext, position);
            }
        });
    }

    private void fillSpinner(final String productName){
        final List<String> profiles = pG.getProfilesStrings(productName);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, profiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                profilesSpinner.setAdapter(null);
                profilesSpinner.setAdapter(adapter);
            }
        });

    }

    private void getVersionsData(){
        devices = mRsContext.queryDevices();
        if(devices.getDeviceCount() > 0){
            selectedDevice = devices.createDevice(0);
            String cameraName = selectedDevice.getInfo(CameraInfo.NAME);
            String cameraSerial = selectedDevice.getInfo(CameraInfo.SERIAL_NUMBER);
            String fwVersion = selectedDevice.getInfo(CameraInfo.FIRMWARE_VERSION);

            camNameTV.setText(cameraName);
            camSerialTV.setText(cameraSerial);
            cameraFWTV.setText(fwVersion);

            fillSpinner(cameraName);
        }
    }

    private void startStream(final Config cfg) throws Exception {

        if (mStreamingThread == null || !mStreamingThread.isAlive()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Update UI Here
                }
            });
            mStreamingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        try(Pipeline pipe = new Pipeline()){
                            PipelineProfile pp = pipe.start(cfg);
                            mIsStreaming = true;
                            lastColorTS = 0;
                            lastDepthTS = 0;
                            lastIrTS = 0;
                            while (!mStreamingThread.isInterrupted())
                                try (FrameSet frames = pipe.waitForFrames()) {
                                    try (final Frame frame = frames.first(StreamType.DEPTH)) {
                                        if(frame != null){
                                            if(firstFrameTime == null)
                                                firstFrameTime = Calendar.getInstance().getTime();
                                            final DepthFrame depth = frame.as(Extension.DEPTH_FRAME);
                                            //final float deptValue = depth.getDistance(depth.getWidth() / 2, depth.getHeight() / 2);
                                            final int frameCount = depth.getNumber();
                                            final double frameTs = depth.getTimestamp();


                                            if(lastDepthTS != 0){
                                                double delta = frameTs - lastDepthTS;
                                                int expectedFps = (int)(pG.getConfig(selectedProfile).get(StreamType.DEPTH.toString()));
                                                double expectedDelta = 1000/ expectedFps;
                                                if(delta > expectedDelta * 1.5) {
                                                    int drops = (int)(Math.round(delta / expectedDelta) - 1);
                                                    depthDrops += drops;
                                                    appendTerminal(drops + " Depth Frame Drops/ Detected");
                                                }
                                            }
                                            lastDepthTS = frameTs;

                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    depthCntTV.setText(Integer.toString(frameCount));
                                                    depthTsTV.setText(Double.toString(frameTs));
                                                    depthDropsTV.setText(Integer.toString(depthDrops));
//                                            mDepthCounter.setText(getString(R.string.depth_counter, frameCount));
//                                            TextView textView = findViewById(R.id.tv_dist);
//                                            textView.setText(String.valueOf(df.format(deptValue)));
                                                }
                                            });
                                            depth.close();
                                        }
                                    }

                                    try (final Frame frame = frames.first(StreamType.COLOR)) {
                                        if(frame != null){
                                            if(firstFrameTime == null)
                                                firstFrameTime = Calendar.getInstance().getTime();
                                            final VideoFrame color = frame.as(Extension.VIDEO_FRAME);
                                            final int frameCount = color.getNumber();
                                            final double frameTs = color.getTimestamp();

                                            if(lastColorTS != 0){
                                                double delta = frameTs - lastColorTS;
                                                int expectedFps = (int)(pG.getConfig(selectedProfile).get(StreamType.COLOR.toString()));
                                                double expectedDelta = 1000/ expectedFps;
                                                if(delta > expectedDelta * 1.5) {
                                                    int drops = (int)(Math.round(delta / expectedDelta) - 1);
                                                    colorDrops += drops;
                                                    appendTerminal(drops + " Color Frame Drop/s Detected");
                                                }
                                            }
                                            lastColorTS = frameTs;

                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    colorCntTV.setText(Integer.toString(frameCount));
                                                    colorTsTV.setText(Double.toString(frameTs));
                                                    colorDropsTV.setText(Integer.toString(colorDrops));
//                                            mVideoCounter.setText(getString(R.string.video_counter, frameCount));
                                                }
                                            });
                                            color.close();
                                        }
                                    }
                                    try (final Frame frame = frames.first(StreamType.INFRARED)) {
                                        if(frame != null){
                                            if(firstFrameTime == null)
                                                firstFrameTime = Calendar.getInstance().getTime();
                                            final VideoFrame infrared = frame.as(Extension.VIDEO_FRAME);
                                            final int frameCount = infrared.getNumber();
                                            final double frameTs = infrared.getTimestamp();

                                            if(lastIrTS != 0){
                                                double delta = frameTs - lastIrTS;
                                                int expectedFps = (int)(pG.getConfig(selectedProfile).get(StreamType.INFRARED.toString()));
                                                double expectedDelta = 1000/ expectedFps;
                                                if(delta > expectedDelta * 1.5) {
                                                    int drops = (int)(Math.round(delta / expectedDelta) - 1);
                                                    irDrops += drops;
                                                    appendTerminal(drops + " IR Frame Drops/ Detected");
                                                }
                                            }
                                            lastIrTS = frameTs;

                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        irCntTV.setText(Integer.toString(frameCount));
                                                        irTsTV.setText(Double.toString(frameTs));
                                                        irDropsTV.setText(Integer.toString(irDrops));
                                                    }
                                                    catch (Exception ex){
                                                        appendTerminal(ex.getMessage());
                                                        Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
                                                    }
//                                            mInfraredCounter.setText(getString(R.string.infrared_counter, frameCount));
                                                    infrared.close();
                                                }
                                            });
                                        }
                                    }

                                    //Calculate the Time To first frame/s
                                    if(timeToFirstFrame == -1){
                                        timeToFirstFrame = firstFrameTime.getTime() - startTime.getTime();
                                        appendTerminal("Time to first frame is: " + timeToFirstFrame);
                                    }
                                }

                            pipe.stop();
                            pp.close();
                            mIsStreaming = false;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        appendTerminal("error: " + e.getMessage());
                        Log.e("ShadisAPP", "error: " + e.toString() + " trace: " + Log.getStackTraceString(e));
                    }
                }
            });
            mStreamingThread.start();
        }
    }

    private void stopStreaming(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //update UI Here
            }
        });
        if (mStreamingThread != null &&mStreamingThread.isAlive())
            mStreamingThread.interrupt();
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

}
