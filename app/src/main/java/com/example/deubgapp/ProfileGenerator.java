package com.example.deubgapp;

import android.graphics.Bitmap;
import android.widget.ListView;

import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.StreamFormat;
import com.intel.realsense.librealsense.StreamType;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

public class ProfileGenerator {
    private List<String> dS5Combinations, ivcamCombinations;

    ProfileGenerator(){

        //Fill the D400 profiles Combinations
        dS5Combinations = new ArrayList<>();
        dS5Combinations.add("Z-640x480-6");
        dS5Combinations.add("Z-640x480-15");
        dS5Combinations.add("Z-640x480-30");
        dS5Combinations.add("Z-640x480-60");
        dS5Combinations.add("Y-640x480-6");
        dS5Combinations.add("Y-640x480-15");
        dS5Combinations.add("Y-640x480-30");
        dS5Combinations.add("Y-640x480-60");
        dS5Combinations.add("YUYV-640x480-30");
        dS5Combinations.add("YUYV-640x480-60");
        dS5Combinations.add("Z-640x480-30 + Y-640x480-30");
        dS5Combinations.add("Z-640x480-30 + YUYV-640x480-30");
        dS5Combinations.add("Z-640x480-30 + Y-640x480-30 + YUYV-640x480-30");

        //Fill the L515 Profiles Combinations
        ivcamCombinations = new ArrayList<>();
        ivcamCombinations.add("Z-640x480-30");
        ivcamCombinations.add("Y-640x480-30");
        ivcamCombinations.add("YUYV-640x480-30");
        ivcamCombinations.add("Z-640x480-30 + Y-640x480-30");
        ivcamCombinations.add("Z-640x480-30 + YUYV-640x480-30");
        ivcamCombinations.add("Z-640x480-30 + Y-640x480-30 + YUYV-640x480-30");
        ivcamCombinations.add("Z-1024x768-30 + Y-1024x768-30 + YUYV-1280x720-30");
    }

    private StreamType getStreamType(String sT){
        switch (sT.toLowerCase()){
            case "z": return StreamType.DEPTH;
            case "y": return StreamType.INFRARED;
            case "yuyv": return StreamType.COLOR;
        }
        return StreamType.ANY;
    }

    private StreamFormat getStreamFormat(String sT){
        switch (sT.toLowerCase()){
            case "z": return StreamFormat.ANY.Z16;
            case "y": return StreamFormat.Y8;
            case "yuyv": return StreamFormat.YUYV;
        }
        return StreamFormat.ANY;
    }

    public Dictionary getConfig(String configStr){
        Dictionary cfg = new Hashtable();
        String item = configStr.replace(" ", "");
        String[] profilesStrings = item.split("\\+");
        for(int j=0; j<profilesStrings.length; j++){
            String[] items = profilesStrings[j].split("-");
            StreamType sT = getStreamType(items[0]);
            StreamFormat fmt = getStreamFormat(items[0]);
            String[] res = items[1].split("x");
            int width = Integer.parseInt(res[0]);
            int height = Integer.parseInt(res[1]);
            int fps = Integer.parseInt(items[2]);
            cfg.put(sT.toString(), fps);
        }
        return cfg;

    }

    public Config parseConfig(String configStr){
        Config cfg = new Config();
        String item = configStr.replace(" ", "");
        String[] profilesStrings = item.split("\\+");
        for(int j=0; j<profilesStrings.length; j++){
            String[] items = profilesStrings[j].split("-");
            StreamType sT = getStreamType(items[0]);
            StreamFormat fmt = getStreamFormat(items[0]);
            String[] res = items[1].split("x");
            int width = Integer.parseInt(res[0]);
            int height = Integer.parseInt(res[1]);
            int fps = Integer.parseInt(items[2]);
            cfg.enableStream(sT, -1, width, height, fmt, fps);
        }
        return cfg;
    }

    public List<Config> getConfigurations(String product){
        List<Config> configurationsToReturn = new ArrayList<>();
        List<String> selectedProduct = new ArrayList<>();

        if(product.toLowerCase().contains("d4"))
            selectedProduct = dS5Combinations;

        if(product.toLowerCase().contains("l5"))
            selectedProduct = ivcamCombinations;

        for(int i=0; i<selectedProduct.size(); i++){
            Config cfg = parseConfig(selectedProduct.get(i));
            configurationsToReturn.add(cfg);
        }

        return configurationsToReturn;
    }

    public List<String> getProfilesStrings(String product){
        if(product.toLowerCase().contains("d4"))
            return dS5Combinations;

        if(product.toLowerCase().contains("l5"))
            return ivcamCombinations;

        return new ArrayList<>();
    }

}
