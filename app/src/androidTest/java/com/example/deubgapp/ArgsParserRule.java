package com.example.deubgapp;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;


public class ArgsParserRule implements TestRule {

    private String mTestConfig;

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Bundle args = InstrumentationRegistry.getArguments();
                if (args == null)
                    mTestConfig = null;
                else{
                    mTestConfig = InstrumentationRegistry.getArguments().getString("test_input");
                }
                base.evaluate();
            }
        };
    }

    public String getTestConfig() {
        return mTestConfig;
    }
}
