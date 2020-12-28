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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class TestModeActivity extends AppCompatActivity {

    TextView CamNameTV, camSerialTV, cameraFWTV;
    ListView profilesLV;
    CheckBox selectAll, selectRandom;
    EditText terminal, iterationsTextBox, durationTextBox, delayAfterStopTextBox;
    Button runTest;

    RsContext mRsContext;
    DeviceList devices;
    Device selectedDevice;
    boolean mIsStreaming;
    private Thread mStreamingThread;
    ProfileGenerator pG;
    List<String> selectedProfiles;
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
    boolean random;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_mode);

        selectedProfiles = new ArrayList<>();
        pG = new ProfileGenerator();
        mIsStreaming = false;
        random = false;

        //Build the view objects
        terminal = findViewById(R.id.terminal);
        CamNameTV = findViewById(R.id.cameraName);
        camSerialTV = findViewById(R.id.camerSerial);
        cameraFWTV = findViewById(R.id.cameraFW);
        profilesLV = findViewById(R.id.profile_list_view);
        selectAll = findViewById(R.id.checkbox_select_all);
        selectRandom = findViewById(R.id.checkbox_random);
        iterationsTextBox = findViewById(R.id.iterations);
        durationTextBox = findViewById(R.id.duration);
        delayAfterStopTextBox = findViewById(R.id.delay_after_stop);
        runTest = findViewById(R.id.run_test);

        RsContext.init(getApplicationContext());
        mRsContext = new RsContext();
        try {
            Thread.sleep(4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        getVersionsData();

        mRsContext.setDevicesChangedCallback(new DeviceListener() {
            @Override
            public void onDeviceAttach() {
                getVersionsData();
            }

            @Override
            public void onDeviceDetach() {
                   Toast.makeText(getApplicationContext(), "Device Detached", Toast.LENGTH_LONG).show();

                devices = mRsContext.queryDevices();
                CamNameTV.setText("");
                camSerialTV.setText("");
                cameraFWTV.setText("");

                //Clear List View
                profilesLV.setAdapter(null);
            }
        });

        selectAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                selectedProfiles = new ArrayList<>();//Clear the Selected Profile List
                for ( int i=0; i < profilesLV.getAdapter().getCount(); i++) {
                    profilesLV.setItemChecked(i, b);
                    if(b)
                        selectedProfiles.add(profilesLV.getItemAtPosition(i).toString());
                }
            }
        });

        selectRandom.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    random = b;
            }
        });

        terminal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideKeyboard(TestModeActivity.this);
            }
        });

        runTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int iterations = Integer.parseInt(iterationsTextBox.getText().toString());
                int duration = Integer.parseInt(durationTextBox.getText().toString());
                int delayAfterStop = Integer.parseInt(delayAfterStopTextBox.getText().toString());
                startStopTest(iterations, duration, random, delayAfterStop, 0);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    private void initListView(final String productName){
        //Get Profiles From Profile Generator according to the product
        final List<String> profiles = pG.getProfilesStrings(productName);
        // Create an ArrayAdapter from List
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>
                (this, android.R.layout.simple_list_item_multiple_choice , profiles);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Clear List View
                profilesLV.setAdapter(null);

                // DataBind ListView with items from ArrayAdapter
                profilesLV.setAdapter(arrayAdapter);

                profilesLV.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                profilesLV.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        boolean isChecked = profilesLV.isItemChecked(i);
                        profilesLV.setItemChecked(i, isChecked);
                        if(isChecked) {
                            selectedProfiles.add(profilesLV.getItemAtPosition(i).toString());
                        }
                        else{
                            selectedProfiles.remove(profilesLV.getItemAtPosition(i).toString());
                        }
                    }
                });
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

            CamNameTV.setText(cameraName);
            camSerialTV.setText(cameraSerial);
            cameraFWTV.setText(fwVersion);

            initListView(cameraName);
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
                                                    appendTerminal(drops + " Depth Frame Drops Detected");
                                                }
                                            }
                                            lastDepthTS = frameTs;
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
                                            infrared.close();
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

    private void startStopTest(final int iterations, final int duration, final boolean random, final int delayAfterStop, final int profileIndex){
        if(!random){
//            for(int i=0; i<iterations; i++){
//                for(int j=0; j<selectedProfiles.size(); j++){
                    if(iterations == 0){
                        appendTerminal("===================================");
                        appendTerminal("============ Test Done ==============");
                        appendTerminal("===================================");
                        return;
                    }
                    if(profileIndex >= selectedProfiles.size()){
                        int leftIterations = iterations - 1;
                        startStopTest(leftIterations, duration, random, delayAfterStop, 0);
                        return;
                    }
            selectedProfile = selectedProfiles.get(profileIndex);
            final Config cfg = pG.parseConfig(selectedProfile);
                    try {
                        startStream(cfg);
                        appendTerminal(selectedProfile + " Streaming Started.... Collecting Frames for " + duration + " seconds");

                        Thread waitThread = new Thread(new Runnable() {
                            @Override
                            public void run(){
                                try {
                                    Thread.sleep(duration * 1000);
                                    stopStreaming();
                                    cfg.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                //wait after stop
                                try {
                                    Thread.sleep(delayAfterStop * 1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                startStopTest(iterations, duration, random, delayAfterStop, profileIndex + 1);
                            }});
                        waitThread.start();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        appendTerminal("Starting streaming Filed on: " + e.getMessage());
                    }
                }
//            }
//        }
    }
}
