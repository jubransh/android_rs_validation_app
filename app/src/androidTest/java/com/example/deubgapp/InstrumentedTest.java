package com.example.deubgapp;

import android.content.Context;
import android.content.SyncStatusObserver;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class InstrumentedTest {
    public ActivityTestRule<UpdateActivity> mActivityRule = new ActivityTestRule(UpdateActivity.class, true, true);
    public ArgsParserRule mTestConfigParser = new ArgsParserRule();
    public UpdateActivity mActivity;
    private String mTestConfig;

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals("com.example.deubgapp", appContext.getPackageName());
    }
    @Rule
    public RuleChain chain = RuleChain.outerRule(mActivityRule).around(mTestConfigParser);

    @Before
    public void testSetup() {
        mActivity = mActivityRule.getActivity();
        mTestConfig = mTestConfigParser.getTestConfig();

    }

    @Test
    public void dumpFlash() throws Exception {
        String testConfig = mTestConfigParser.getTestConfig();
        if (testConfig == null)
            throw new Exception("No Valid Args");

        String path = testConfig;
        Thread dumpThread = mActivity.dumpFlash(path);
        dumpThread.start();
        dumpThread.join();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void vanilla() throws Exception {
        String testConfig = mTestConfigParser.getTestConfig();
        if (testConfig == null){
//            throw new Exception("No Valid Args");
            testConfig = "DebugApp/Signed_Image_UVC_15_4_0_0.bin";
        }


        String path = testConfig;
        Thread vanillaThread = mActivity.updateVanilla(path);
        vanillaThread.start();
        vanillaThread.join();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void eraseRW() throws Exception {
        Thread eraseThread = mActivity.eraseFlashRW();
        if(eraseThread == null)
            System.out.println("NULLL");
        eraseThread.start();
        eraseThread.join();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void writeFlashRW() throws Exception {
        String testConfig = mTestConfigParser.getTestConfig();
        if (testConfig == null)
            testConfig = "/DebugApp/dump.bin";
//            throw new Exception("No Valid Args");

        String path = testConfig;
        Thread writeThread = mActivity.writeRWImage(path);
        writeThread.start();
        writeThread.join();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void resetCamera() throws Exception {
        Thread resetThread = mActivity.resetCamera();
        resetThread.start();
        resetThread.join();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}