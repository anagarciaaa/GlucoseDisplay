package io.hammerhead.samplemodule;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import io.hammerhead.sample.KeyConstants;
import io.hammerhead.sample.service.PartnerService;
import io.hammerhead.sdk.v0.SdkContext;

public class ConnexContext {
    private final SdkContext sdkContext;

    //TODO: make these atomic doubles, doesn't matter too much for now
    private double partnerMaxHR = 0;
    private double partnerFTP = 0;

    Messenger serviceMessenger;

    //used to receive max HR and max power from the service
    final Messenger connexMessenger;

    ServiceConnection serviceConnection;

    public ConnexContext(SdkContext context) {
        sdkContext = context;

        connexMessenger = new Messenger(new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                Bundle bundle = message.getData();
                double value = bundle.getDouble(KeyConstants.METRIC_VALUE);
                if(message.what == PartnerService.FitnessMetric.MAX_HEART_RATE.ordinal()){
                    partnerMaxHR = value;
                }
                else if(message.what == PartnerService.FitnessMetric.FTP.ordinal()){
                    partnerFTP = value;
                }
                return true;
            }
        }));

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                serviceMessenger = new Messenger(iBinder);
                sendGetDataMessage(connexMessenger, PartnerService.FitnessMetric.MAX_HEART_RATE);
                sendGetDataMessage(connexMessenger, PartnerService.FitnessMetric.FTP);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                serviceMessenger = null;
            }
        };
    }

    public SdkContext getSdkContext() {
        return sdkContext;
    }

    public double getPartnerMaxHR() {
        return partnerMaxHR;
    }

    public double getPartnerFTP() {
        return partnerFTP;
    }

    public void bindToService(){
        if(serviceMessenger != null){
            return;
        }
        Intent intent = new Intent(PartnerService.BIND_REMOTE);
        intent.setComponent(new ComponentName("io.hammerhead.sample", "io.hammerhead.sample.service.PartnerService"));
        sdkContext.getApplicationContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void unbindFromService(){
        if(serviceMessenger == null){
            return;
        }
        sdkContext.getApplicationContext().unbindService(serviceConnection);
        serviceMessenger = null;
    }

    public boolean isBoundToService(){
        return serviceMessenger != null;
    }

    private boolean sendMessage(Messenger replyTo, int msgType, int dataType, double value){
        if(serviceMessenger == null){
            return false;
        }
        Message msg = Message.obtain(null, msgType, dataType, 0);
        if(msgType == PartnerService.MessageType.SET_METRIC.ordinal()){
            Bundle bundle = new Bundle();
            bundle.putDouble(KeyConstants.METRIC_VALUE, value);
            msg.setData(bundle);
        }
        msg.replyTo = replyTo;
        try{
            serviceMessenger.send(msg);
        }
        catch(RemoteException e){
            return false;
        }
        return true;
    }

    //sends message to service to retrive a data/sensor value
    //returns true/false if successful/unsuccessful
    public boolean sendGetDataMessage(Messenger replyTo, PartnerService.FitnessMetric dataType) {
        if(replyTo == null){
            return false;
        }
        return sendMessage(replyTo, PartnerService.MessageType.GET_METRIC.ordinal(), dataType.ordinal(), 0);
    }

    public boolean sendSetDataMessage(PartnerService.FitnessMetric dataType, double value) {
        return sendMessage(null, PartnerService.MessageType.SET_METRIC.ordinal(), dataType.ordinal(), value);
    }
}
