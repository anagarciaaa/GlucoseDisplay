package io.hammerhead.sample.service;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.hammerhead.samplemodule.ConnexxDataType;
import timber.log.Timber;

public class ConnectionHandler {
    private final Context serviceCtx;
    private final BluetoothDevice device;
    private final BluetoothAdapter adapter;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket clientSocket;
    private final Object socketLock = new Object();

    private final String LOG_TAG = "ConnectionThread";
    private final String userName;
    private Thread connectionThread;
    private Thread readerThread;
    private AtomicBoolean shouldStop = new AtomicBoolean(false);
    private boolean manualDisconnectTriggered = false;
    private double userMaxHR = ConnexxDataType.INVALID_VALUE;
    private double userFTP = ConnexxDataType.INVALID_VALUE;
    private String partnerName;
    private final ConnectionListener connectionListener;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private int retryCount = 0;
    private final int MAX_RETRIES = 8;

    public ConcurrentHashMap<String, Double> partnerData = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, Double> userData = new ConcurrentHashMap<>();

    ConnectionHandler(
            Context serviceCtx,
            BluetoothAdapter adapter,
            BluetoothDevice device,
            String userName,
            ConnectionListener connectionListener
    ) {
        this.device = device;
        this.adapter = adapter;
        this.serviceCtx = serviceCtx;
        this.userName = userName;
        this.connectionListener = connectionListener;

        boolean nameChanged = adapter.setName(userName);
        String logMsg = nameChanged ? ("Name changed to " + userName) : "Name not changed";
        Timber.tag(LOG_TAG).i(logMsg);
    }

    public enum ConnectionState {
        RETRYING,
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }

    private void changeState(ConnectionState newState){
        connectionState = newState;
        Timber.tag(LOG_TAG).i("Connection state changed to %s, running onStateChanged callback", newState);
        connectionListener.onStateChanged(newState);
        if(newState == ConnectionState.RETRYING){
            Timber.tag(LOG_TAG).i("Connection encountered error, running onRestart callback");
            connectionListener.onRetry(retryCount);
        }
        else if(newState == ConnectionState.DISCONNECTED){
            Timber.tag(LOG_TAG).i("Disconnecting, running onDisconnected callback");
            connectionListener.onDisconnected();
        }
    }

    public void setUserMaxHR(double userMaxHR){
        this.userMaxHR = userMaxHR;
    }

    public void setUserFTP(double userFTP){
        this.userFTP = userFTP;
    }

    public String getPartnerName(){
        return partnerName;
    }

    public void stopHandler(){
        //Close serversocket and client socket,
        // allows us to cancel accept(), listen(), and connect calls
        synchronized (socketLock){
            manualDisconnectTriggered = true;
            if(clientSocket != null){
                try{
                    OutputStream outputStream = clientSocket.getOutputStream();
                    writeStringToStream(outputStream, "DISCONNECT:0;");
                    Timber.tag(LOG_TAG).i("Disconnect message sent");
                    clientSocket.close();
                }
                catch(IOException e){
                    Timber.tag(LOG_TAG).e(e, "Error closing partner socket");
                }
            }
            if(serverSocket != null){
                try{
                    serverSocket.close();
                }
                catch(IOException e){
                    Timber.tag(LOG_TAG).e(e, "Error closing server socket");
                }
            }

            shouldStop.set(true);
        }

    }

    public void startAsServer(){
        connectionThread = new Thread(() -> {
            waitForClientConnection();
            runPartnerCommunication();
        });
        connectionThread.start();
    }

    public void startAsClient(){
        connectionThread = new Thread(() -> {
            connectToServerSocket(device);
            runPartnerCommunication();
        });
        connectionThread.start();
    }

    public void waitForThread(){
        if(connectionThread != null){
            try{
                connectionThread.join();
            }
            catch (InterruptedException e){
                Timber.tag(LOG_TAG).e(e, "Error waiting for connection thread");
            }
        }
    }

    private void tryRestart(){
        if(manualDisconnectTriggered){
            Timber.tag(LOG_TAG).i("Manual disconnect triggered, disconnecting");
            changeState(ConnectionState.DISCONNECTED);
            return;
        }
        retryCount++;
        Timber.tag(LOG_TAG).i("Retry count: %d", retryCount);
        if(retryCount < MAX_RETRIES){
            Timber.tag(LOG_TAG).i("Changing state to RESTARTING");
            changeState(ConnectionState.RETRYING);
        }
        else{
            Timber.tag(LOG_TAG).i("Max retries reached, changing state to DISCONNECTED");
            changeState(ConnectionState.DISCONNECTED);
        }
    }

    private void closeSockets(){
        try {
            synchronized (socketLock){
                if(serverSocket != null){
                    serverSocket.close();
                    serverSocket = null;
                }
                if(clientSocket != null){
                    clientSocket.close();
                    clientSocket = null;
                }
                Timber.tag(LOG_TAG).i("Sockets closed");
            }
        }
        catch(IOException e){
            Timber.tag(LOG_TAG).e(e, "Error closing server socket");
        }
    }

    private void runPartnerCommunication(){
        //TODO: communicate error to user
        if(connectionState != ConnectionState.CONNECTED){
            Timber.tag(LOG_TAG).e("Error creating client socket");
            closeSockets();
            tryRestart();
            return;
        }

        try{
            handleConnection();
        }
        catch (IOException e){
            Timber.tag(LOG_TAG).e(e, "Error handling/closing incoming connection");
        }
        closeSockets();
        shouldStop.set(false);
        tryRestart();
    }

    private String createDataString(){
        StringBuilder dataString = new StringBuilder();
        userData.forEach((key, value) -> {
            dataString.append(String.format("%s:%f;", key, value));
        });
        return dataString.toString();
    }

    private static void writeStringToStream(OutputStream outputStream, String dataString) throws IOException {
        byte[] dataBytes = dataString.getBytes();
        byte dataLength = (byte) dataBytes.length;
        byte[] dataBuffer = new byte[dataLength + 1];
        dataBuffer[0] = dataLength;
        System.arraycopy(dataBytes, 0, dataBuffer, 1, dataBytes.length);
        outputStream.write(dataBuffer);
    }

    private void handleConnection() throws IOException {
        InputStream inputStream;
        OutputStream outputStream;
        try {
             inputStream = clientSocket.getInputStream();
             outputStream = clientSocket.getOutputStream();
        } catch (IOException e) {
            Timber.tag(LOG_TAG).e(e, "Error getting input/output streams");
            throw e;
        }
        //write username to output stream
        Timber.tag(LOG_TAG).i("Sending username: %s", userName);
        writeStringToStream(outputStream, userName);

        //write user max HR and max power to output stream
        String maxValueString = "";
        if(userMaxHR != ConnexxDataType.INVALID_VALUE){
            maxValueString += String.format("%s:%f;", PartnerService.FitnessMetric.MAX_HEART_RATE.name(), userMaxHR);
        }
        if(userFTP != ConnexxDataType.INVALID_VALUE){
            maxValueString += String.format("%s:%f;", PartnerService.FitnessMetric.FTP.name(), userFTP);
        }
        if(!maxValueString.isEmpty()){
            Timber.tag(LOG_TAG).i("Sending max values: %s", maxValueString);
            writeStringToStream(outputStream, maxValueString);
        }
        else{
            Timber.tag(LOG_TAG).i("No max values to send");
        }

        //read partner name from input stream, should always be first message
        byte partnerNameLength = (byte) inputStream.read();
        byte[] partnerNameBuffer = new byte[partnerNameLength];
        int numBytesRead = inputStream.read(partnerNameBuffer);
        this.partnerName = new String(partnerNameBuffer, 0, numBytesRead);
        Timber.tag(LOG_TAG).i("Received partner name: %s", this.partnerName);
        this.connectionListener.onPartnerNameReceived(this.partnerName);


        readerThread = new Thread(() -> {
            readData(inputStream);
        });
        readerThread.start();

        //periodically send data to partner
        while(!shouldStop.get()){
            String dataString = createDataString();
            if(!dataString.isEmpty()){
                writeStringToStream(outputStream, dataString);
            }
            else{
                //Timber.tag(LOG_TAG).w("No data to send");
            }
            try{
                Thread.sleep(800);
            }
            catch(InterruptedException e){
                Timber.tag(LOG_TAG).e(e, "Error sleeping");
            }
        }
    }

    private void readData(InputStream inputStream){
        while(!shouldStop.get()){
            byte inDataLen;
            byte[] inDataBuffer;
            int numBytesRead;
            try{
                inDataLen = (byte) inputStream.read();
                inDataBuffer = new byte[inDataLen];
                numBytesRead = inputStream.read(inDataBuffer);
            }
            catch(IOException e){
                Timber.tag(LOG_TAG).e(e, "Error reading data from input stream");
                shouldStop.set(true);
                return;
            }
            String inData = new String(inDataBuffer, 0, numBytesRead);
            //Timber.tag(LOG_TAG).i("Received data: %s", inData);
            String[] dataItems = inData.split(";");
            for(String dataItem: dataItems){
                String[] keyValue = dataItem.split(":");
                if(keyValue.length != 2){
                    Timber.tag(LOG_TAG).w("Invalid data item: %s", dataItem);
                    continue;
                }
                String key = keyValue[0];
                double value;
                try{
                    value = Double.parseDouble(keyValue[1]);
                }
                catch(NumberFormatException e){
                    Timber.tag(LOG_TAG).w("Invalid value for key: %s", key);
                    continue;
                }

                PartnerService.FitnessMetric[] fitnessMetrics = PartnerService.FitnessMetric.values();
                boolean keyIsNotMetric = Arrays.stream(fitnessMetrics).noneMatch(metricType -> key.equals(metricType.name()));
                if(keyIsNotMetric && !key.equals("DISCONNECT")){
                    Timber.tag(LOG_TAG).w("Invalid key: %s", key);
                    continue;
                }
                partnerData.put(key, value);
                if(key.equals(PartnerService.FitnessMetric.HEART_RATE.name())){
                    //Timber.tag(LOG_TAG).i("Received HR: %f", value);
                }
                else if(key.equals(PartnerService.FitnessMetric.MAX_HEART_RATE.name())){
                    Timber.tag(LOG_TAG).i("Received max hr: %f", value);
                    this.connectionListener.onPartnerMetricReceived(PartnerService.FitnessMetric.MAX_HEART_RATE, value);
                }
                else if(key.equals(PartnerService.FitnessMetric.FTP.name())) {
                    Timber.tag(LOG_TAG).i("Received max power: %f", value);
                    this.connectionListener.onPartnerMetricReceived(PartnerService.FitnessMetric.FTP, value);
                }
                else if(key.equals("DISCONNECT")){
                    Timber.tag(LOG_TAG).i("Received disconnect message");
                    manualDisconnectTriggered = true;
                    shouldStop.set(true);
                    return;
                }
            }
        }
    }

    private void connectToServerSocket(BluetoothDevice device) {
        Timber.tag(LOG_TAG).i("Starting connection to partner");
        if(this.adapter.isDiscovering()){
            Timber.tag(LOG_TAG).i("Cancelling discovery");
            this.adapter.cancelDiscovery();
        }
        if(this.device == null){
            Timber.tag(LOG_TAG).e("Client connection started with null device");
            return;
        }
        //If it's not 0, we are restarting and should not change state
        if(retryCount == 0){
            changeState(ConnectionState.CONNECTING);
        }
        try{
            synchronized (socketLock){
                if(manualDisconnectTriggered){
                    return;
                }
                clientSocket = this.device.createInsecureRfcommSocketToServiceRecord(PartnerService.SERVICE_UUID);
            }
            clientSocket.connect();
            Timber.tag(LOG_TAG).i("Connection established");
        }
        catch(IOException e){
            Timber.tag(LOG_TAG).e(e, "Error creating/connecting to client socket");
            return;
        }
        changeState(ConnectionState.CONNECTED);
    }

    private void waitForClientConnection(){
        if(this.adapter.isDiscovering()){
            Timber.tag(LOG_TAG).i("Cancelling discovery");
            this.adapter.cancelDiscovery();
        }
        //If it's not 0, we are restarting and should not change state
        if(retryCount == 0){
            changeState(ConnectionState.CONNECTING);
        }
        try{
            Timber.tag(LOG_TAG).i("Listening for incoming connection");

            synchronized (socketLock){
                if(manualDisconnectTriggered){
                    return;
                }
                serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord("ConneXX", PartnerService.SERVICE_UUID);
            }
            clientSocket = serverSocket.accept();
            Timber.tag(LOG_TAG).i("Connection accepted");
        }
        catch(IOException e){
            Timber.tag(LOG_TAG).e(e, "Error listening/accepting for incoming connections");
            return;
        }
        changeState(ConnectionState.CONNECTED);
    }
}
