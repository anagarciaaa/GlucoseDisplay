package io.hammerhead.samplemodule;

import androidx.annotation.NonNull;

import java.util.Map;

import io.hammerhead.sample.service.PartnerService;
import io.hammerhead.sdk.v0.datatype.Dependency;
import io.hammerhead.sdk.v0.datatype.transformer.SdkTransformer;

public class UserDataTypeTransformer extends SdkTransformer {
    ConnexContext connexContext;
    final Dependency dependency;
    final PartnerService.FitnessMetric fitnessMetric;
    public UserDataTypeTransformer(
            @NonNull ConnexContext connexContext,
            Dependency dependency,
            PartnerService.FitnessMetric fitnessMetric
    ) {
        super(connexContext.getSdkContext());
        this.connexContext = connexContext;
        this.dependency = dependency;
        this.fitnessMetric = fitnessMetric;
    }

    @Override
    public double onDependencyChange(long timeStamp, @NonNull Map<Dependency, Double> dependencies) {
        Double value = dependencies.get(dependency);
        if(value == null) {
            value = ConnexxDataType.INVALID_VALUE;
        }
        connexContext.sendSetDataMessage(fitnessMetric, value);
        return value;
    }
}
