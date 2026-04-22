package io.hammerhead.sample.service;
import io.hammerhead.sample.service.ConnectionHandler.ConnectionState;

public interface ConnectionListener {
    void onStateChanged(ConnectionState state);
    void onPartnerNameReceived(String partnerName);
    //Only called with maxHR and maxFTP metrics
    void onPartnerMetricReceived(PartnerService.FitnessMetric metric, double value);
    void onDisconnected();
    void onRetry(int retryCount);
}
