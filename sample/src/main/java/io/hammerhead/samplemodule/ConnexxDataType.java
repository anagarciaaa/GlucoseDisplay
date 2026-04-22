package io.hammerhead.samplemodule;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import io.hammerhead.sample.service.PartnerService;
import io.hammerhead.sdk.v0.datatype.Dependency;
import io.hammerhead.sdk.v0.datatype.SdkDataType;
import io.hammerhead.sdk.v0.datatype.formatter.SdkFormatter;
import io.hammerhead.sdk.v0.datatype.transformer.SdkTransformer;
import io.hammerhead.sdk.v0.datatype.view.BuiltInView;
import io.hammerhead.sdk.v0.datatype.view.SdkView;

public class ConnexxDataType extends SdkDataType {
    private ConnexContext connexContext;
    private SdkTransformer transformer;
    private ConnexxDataTypeFormatter formatter = new ConnexxDataTypeFormatter(0, false);

    final String typeId;
    final String displayName;
    final String description;
    final Dependency dependency;
    final PartnerService.FitnessMetric fitnessMetric;
    final double sampleValue;
    final boolean isUserDataType;

    /*The KarooSdk provides a MISSING_VALUE constant for the same reason
    this is used here. However sometimes when a transformer returns MISSING_VALUE,
    it displays 0, without calling the formatter. This is why we use a different
    constant here.
     */
    public static double INVALID_VALUE = -1;

    public ConnexxDataType(
            ConnexContext connexContext,
            String typeId,
            String displayName,
            String description,
            PartnerService.FitnessMetric fitnessMetric,
            Dependency dependency,
            double sampleValue,
            boolean isUserDataType
    ) {
        super(connexContext.getSdkContext());
        this.connexContext = connexContext;
        this.typeId = typeId;
        this.displayName = displayName;
        this.description = description;
        this.dependency = dependency;
        this.fitnessMetric = fitnessMetric;
        this.sampleValue = sampleValue;
        this.isUserDataType = isUserDataType;
        this.transformer = isUserDataType ?
                new UserDataTypeTransformer(this.connexContext, dependency, fitnessMetric) :
                new PartnerDataTypeTransformer(this.connexContext, fitnessMetric);
    }

    public void setFormatterPrecision(int precision) {
        this.formatter.precision = precision;
    }

    // Updates formatter to use percentages and configures
    // the partner transformer to calculate a percentage using the given data type
    public void setPercentageFormatter(PartnerService.FitnessMetric dataType) {
        this.formatter.usePercentage = true;
        if(isUserDataType){
            return;
        }
        try{
            PartnerDataTypeTransformer pTransformer = (PartnerDataTypeTransformer) this.transformer;
            pTransformer.setPercentageType(dataType);
        }
        catch (ClassCastException e){
            // Do nothing
        }
    }

    @NotNull
    @Override
    public String getTypeId() {
        return typeId;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    @Override
    public String getDescription() {
        return description;
    }

    @NotNull
    @Override
    public List<Dependency> getDependencies() {
        return Arrays.asList(dependency);
    }

    @Override
    public double getSampleValue() {
        return sampleValue;
    }

    @NotNull
    @Override
    public SdkView newView() {
        return new BuiltInView.Numeric(this.getContext());
    }

    @NotNull
    @Override
    public SdkFormatter newFormatter() {
        return formatter;
    }

    @NotNull
    @Override
    public SdkTransformer newTransformer() {
        return transformer;
    }
}
