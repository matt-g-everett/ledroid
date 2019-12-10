package com.matteverett.ledroid;

import android.content.Context;
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

import java.util.List;
import java.util.Locale;

public final class Calibrate {
    private static final String TAG = "ledroid::Calibrate";

    private static final String serverUri = "tcp://10.0.0.1:1883";
    private static final String username = "user";
    private static final String password = "password";
    private static final String clientId = "ledroid";
    private static final String subscriptionTopic = "home/xmastree/cal/server";
    private static final String publishTopic = "home/xmastree/cal/client";

    private MqttAndroidClient mqttAndroidClient;
    private List<Point> mLocations;

    Calibrate() {
        Log.i(TAG, "Calibrate created");
    }

    void Init(Context context) {
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
                SendLocations();
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
            if(!mqttAndroidClient.isConnected()){
                Log.i(TAG, mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    void StoreLocations(List<Point> locations) {
        mLocations = locations;
    }

    void SendLocations(){
        try {
            String payload = "{\"type\":\"data\",\"locations\":[";
            for (int i = 0; i < mLocations.size(); i++) {
                payload += String.format(Locale.ENGLISH, "%f,%f", mLocations.get(i).x, mLocations.get(i).y);
                if (i < mLocations.size() - 1) {
                    payload += ",";
                }
            }
            payload += "]}";

            MqttMessage message = new MqttMessage();
            message.setPayload(payload.getBytes());
            message.setQos(0);

            mqttAndroidClient.publish(publishTopic, message);
            if(!mqttAndroidClient.isConnected()){
                Log.i(TAG, mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
