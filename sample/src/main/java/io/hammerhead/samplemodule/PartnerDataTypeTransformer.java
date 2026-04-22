package io.hammerhead.samplemodule;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import androidx.annotation.NonNull;

import java.util.Map;

import io.hammerhead.sample.KeyConstants;
import io.hammerhead.sample.service.PartnerService;
import io.hammerhead.sdk.v0.datatype.Dependency;
import io.hammerhead.sdk.v0.datatype.transformer.SdkTransformer;

public class PartnerDataTypeTransformer extends SdkTransformer {
    ConnexContext connexContext;
    final PartnerService.FitnessMetric dataType;
    double partnerValue = ConnexxDataType.INVALID_VALUE;
    PartnerService.FitnessMetric percentageType;

    Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            Bundle bundle = message.getData();
            partnerValue = bundle.getDouble(KeyConstants.METRIC_VALUE);
            return true;
        }
    }));

    public PartnerDataTypeTransformer(@NonNull ConnexContext connexContext, PartnerService.FitnessMetric dataType) {
        super(connexContext.getSdkContext());
        this.connexContext = connexContext;
        this.dataType = dataType;
    }

    public void setPercentageType(PartnerService.FitnessMetric percentageType) {
        this.percentageType = percentageType;
    }

    @Override
    public double onDependencyChange(long timeStamp, @NonNull Map<Dependency, Double> dependencies) {
        if(!connexContext.isBoundToService()) {
            return ConnexxDataType.INVALID_VALUE;
        }
        connexContext.sendGetDataMessage(messenger, dataType);
        if(percentageType == null){
            return partnerValue;
        }
        if(partnerValue == ConnexxDataType.INVALID_VALUE){
            return ConnexxDataType.INVALID_VALUE;
        }
        double maxValue = 0;
        if(percentageType == PartnerService.FitnessMetric.MAX_HEART_RATE) {
            maxValue = connexContext.getPartnerMaxHR();
        }
        else if(percentageType == PartnerService.FitnessMetric.FTP) {
            maxValue = connexContext.getPartnerFTP();
        }
        return maxValue > 0 ? partnerValue / maxValue * 100 : ConnexxDataType.INVALID_VALUE;
    }
}
