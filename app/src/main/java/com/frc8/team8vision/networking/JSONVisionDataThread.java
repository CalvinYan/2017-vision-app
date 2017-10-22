package com.frc8.team8vision.networking;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.frc8.team8vision.util.Constants;
import com.frc8.team8vision.vision.VisionInfoData;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;

import java.util.HashMap;

/**
 * Writes image data to JSON to be read by the RoboRIO
 *
 * @author Quintin Dwight
 */
public class JSONVisionDataThread extends AbstractVisionThread {

    // Instance and state variables
    public static JSONVisionDataThread s_instance;
    private Activity m_activity;

    /**
     * Creates a instance of this
     * Cannot be called outside as a Singleton
     */
    private JSONVisionDataThread() {
        super("JSONVisionDataThread");
    }

    /**
     * @return The instance of the singleton
     */
    public static JSONVisionDataThread getInstance(){
        if(s_instance == null)
            s_instance = new JSONVisionDataThread();
        return s_instance;
    }

    /**
     * Start the thread.
     *
     * @param activity The current android activity.
     */
    public void start(Activity activity) {

        m_activity = activity;

        super.start(Constants.kDataUpdateRateMS);
    }

    @Override
    protected void init() {

        if (s_instance == null) {
            Log.e(k_tag, "No initialized instance in start, this should never happen!");
        }
    }

    @Override
    protected void onPause() {}

    @Override
    public void onResume() {}

    @Override
    public void onStop() {}

    /**
     * Writes matrix data to JSON file
     */
    private void writeVisionData() {

        final JSONObject json = VisionInfoData.getJsonRepresentation();
        writeJSON(json);
    }

//    /**
//     * Converts Matrix representing image data to byte array
//     * @param image Image data
//     * @return Computed byte array
//     */
//    private byte[] toByteArray(Mat image) {
//        byte[] retval = new byte[image.rows() * image.cols() * image.channels() * 8];
//        int index = 0;
//        for (int i = 0; i < image.rows(); i++) {
//            for (int j = 0; j < image.cols(); j++) {
//                double[] pixel = image.get(i, j);
//                for (int k = 0; k < image.channels(); k++) {
//                    byte[] bytes = new byte[8];
//                    ByteBuffer.wrap(bytes).putDouble(pixel[k]);
//                    for (byte b : bytes) retval[index++] = b;
//                }
//            }
//        }
//        return retval;
//    }

    /**
     * Takes JSONObject and writes data to file.
     *
     * @param json JSONObject storing vision data.
     */
    private void writeJSON(JSONObject json) {

        try {

            OutputStreamWriter osw = new OutputStreamWriter(m_activity.openFileOutput("data.json", Context.MODE_PRIVATE));
            osw.write(json.toString());
            osw.flush();
            osw.close();

        } catch (IOException e) {

            Log.e(k_tag, "Could not write data in JSON form: " + e.toString());
        }
    }

    @Override
    protected void update() {

        switch (m_threadState) {

            case RUNNING: {
                writeVisionData();
                break;
            }
        }
    }
}
