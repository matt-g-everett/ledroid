package com.matteverett.ledroid;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.opencv.core.Point;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class Calibrate {
    private static final String TAG = "ledroid::Calibrate";
    private static final String clientId = "ledroid";


    private String serverUri = "";
    private String username = "";
    private String password = "";
    private String subscriptionTopic = "";
    private String publishTopic = "";
    private int captureCount = 20;

    private MqttAndroidClient mqttAndroidClient;
    private List<List<Point>> mLocations = null;

    Calibrate() {
        Log.i(TAG, "Calibrate created");
    }

    void Init(Context context) {
        // Load settings from a resources file
        Resources resources = context.getResources();
        InputStream rawResource = resources.openRawResource(R.raw.config);
        Properties properties = new Properties();
        try {
            properties.load(rawResource);

            serverUri = properties.getProperty("mqttServerUrl");
            username = properties.getProperty("mqttUserName");
            password = properties.getProperty("mqttPassword");
            subscriptionTopic = properties.getProperty("mqttCalServerTopic");
            publishTopic = properties.getProperty("mqttCalClientTopic");
            captureCount = Integer.parseInt(properties.getProperty("captureCount", "20"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        mqttAndroidClient = new MqttAndroidClient(context, serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

                if (reconnect) {
                    Log.i(TAG, "Reconnected");
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopic();
                } else {
                    Log.i(TAG, "Connected to: " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.i(TAG, "The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.i(TAG, "Incoming message: " + new String(message.getPayload()));

                // Start capturing
                mLocations = new ArrayList<>();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setUserName(username);
        mqttConnectOptions.setPassword(password.toCharArray());
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        try {
            //addToHistory("Connecting to " + serverUri);
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Failed to connect to: " + serverUri);
                }
            });


        } catch (MqttException ex){
            ex.printStackTrace();
        }
    }

    private void subscribeToTopic(){
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Subscribed to " + subscriptionTopic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "Failed to subscribe to " + subscriptionTopic);
                }
            });
        } catch (MqttException ex){
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    void StartCalibration(){
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload("{\"type\":\"start\"}".getBytes());
            message.setQos(0);
            mqttAndroidClient.publish(publishTopic, message);
            Log.i(TAG, String.format("Sent message: %s", message));
            if(!mqttAndroidClient.isConnected()){
                Log.i(TAG, mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    void StoreLocations(List<Point> locations) {
        if (mLocations != null) {
            mLocations.add(locations);
            if (mLocations.size() == captureCount) {
                SendLocations(mLocations);

                // Stop capturing
                mLocations = null;
            }
        }
    }

    void SendLocations(List<List<Point>> data){
        try {
            String payload = "{\"type\":\"data\",\"locations\":[";
            for (int i = 0; i < data.size(); i++) {
                payload += '[';

                List<Point> locs = data.get(i);
                for (int j = 0; j < locs.size(); j++) {
                    payload += String.format(Locale.ENGLISH, "%f,%f", locs.get(j).x, locs.get(j).y);
                    if (j < locs.size() - 1) {
                        payload += ",";
                    }
                }

                payload += ']';
                if (i < data.size() - 1) {
                    payload += ",";
                }
            }
            payload += "]}";

            MqttMessage message = new MqttMessage();
            message.setPayload(payload.getBytes());
            message.setQos(0);

            mqttAndroidClient.publish(publishTopic, message);
            Log.i(TAG, String.format("Sent message: %s", message));

            if(!mqttAndroidClient.isConnected()){
                Log.i(TAG, mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
