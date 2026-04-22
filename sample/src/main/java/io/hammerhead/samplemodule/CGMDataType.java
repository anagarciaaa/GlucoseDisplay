package io.hammerhead.samplemodule;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import io.hammerhead.sample.cgm.CGMStore;
import io.hammerhead.sample.cgm.GlucosePacket;
import io.hammerhead.samplemodule.CGM.CGMFormatter;
import io.hammerhead.samplemodule.CGM.CGMTransformer;
import io.hammerhead.sdk.v0.SdkContext;
import io.hammerhead.sdk.v0.datatype.Dependency;
import io.hammerhead.sdk.v0.datatype.SdkDataType;
import io.hammerhead.sdk.v0.datatype.formatter.SdkFormatter;
import io.hammerhead.sdk.v0.datatype.transformer.SdkTransformer;
import io.hammerhead.sdk.v0.datatype.view.BuiltInView;
import io.hammerhead.sdk.v0.datatype.view.SdkView;

public class CGMDataType extends SdkDataType {

    private final ConnexContext connexContext;

    public CGMDataType(@NotNull SdkContext sdkContext) {
        this(sdkContext, null);
    }

    public CGMDataType(@NotNull SdkContext sdkContext, ConnexContext connexContext) {
        super(sdkContext);
        this.connexContext = connexContext;
    }

    @NotNull
    @Override
    public String getTypeId() {
        return "user-glucose";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "User Glucose";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Live blood glucose from the Dexcom Stelo CGM sensor";
    }

    @Override
    public double getSampleValue() {
        GlucosePacket packet = CGMStore.get().getLatest();
        if (packet != null && packet.isUsable()) {
            return packet.glucoseMgDl;
        }
        return 103;
    }

    @NotNull
    @Override
    public List<Dependency> getDependencies() {
        return Arrays.asList(Dependency.INTERVAL);
    }

    @NotNull
    @Override
    public SdkTransformer newTransformer() {
        return new CGMTransformer(this.getContext());
    }

    @NotNull
    @Override
    public SdkFormatter newFormatter() {
        return new CGMFormatter();
    }

    @NotNull
    @Override
    public SdkView newView() {
        return new BuiltInView.Numeric(this.getContext());
    }
}
