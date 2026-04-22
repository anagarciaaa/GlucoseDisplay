/**
 * Copyright (c) 2021 Hammerhead Navigation Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hammerhead.samplemodule;

import org.jetbrains.annotations.NotNull;

import android.content.Intent;

import java.util.Arrays;
import java.util.List;

import io.hammerhead.sample.service.PartnerService;
import io.hammerhead.sample.cgm.CGMService;
import io.hammerhead.sdk.v0.Module;
import io.hammerhead.sdk.v0.ModuleFactoryI;
import io.hammerhead.sdk.v0.SdkContext;
import io.hammerhead.sdk.v0.datatype.Dependency;
import io.hammerhead.sdk.v0.datatype.SdkDataType;
import timber.log.Timber;

public class ConnexModule extends Module {
    public static ModuleFactoryI factory = new ModuleFactoryI() {
        @Override
        public Module buildModule(@NotNull SdkContext context) {
            return new ConnexModule(context);
        }
    };

    private ConnexContext connexContext;

    public ConnexModule(SdkContext sdkContext) {
        super(sdkContext);
        connexContext = new ConnexContext(sdkContext);
    }

    @NotNull
    @Override
    public String getName() {
        return "ConneXX";
    }

    @NotNull
    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean onStart() {
        Timber.i("ConnexModule received ride start event");
        connexContext.bindToService();
        // Start the CGM service when a ride begins
        Intent cgmIntent = new Intent(getContext().getApplicationContext(), CGMService.class);
        getContext().getApplicationContext().startService(cgmIntent);
        return super.onStart();
    }

    @Override
    public boolean onEnd() {
        Timber.i("ConnexModule received ride stop event");
        connexContext.unbindFromService();
        return super.onEnd();
    }

    @NotNull
    @Override
    public List<SdkDataType> provideDataTypes() {
        android.util.Log.e("CGMDEBUG", "provideDataTypes was called");

        ConnexxDataType userHRDataType = new ConnexxDataType(
                connexContext,
                "user-hr",
                "User HR",
                "Displays heart rate and sends data to partner",
                PartnerService.FitnessMetric.HEART_RATE,
                Dependency.HEART_RATE,
                99,
                true
        );

        ConnexxDataType userPowerDataType = new ConnexxDataType(
                connexContext,
                "user-power",
                "User Power",
                "Displays power and sends data to partner",
                PartnerService.FitnessMetric.POWER,
                Dependency.POWER,
                99.0,
                true
        );

        ConnexxDataType userSpeedDataType = new ConnexxDataType(
                connexContext,
                "user-speed",
                "User Speed",
                "Displays speed and sends data to partner",
                PartnerService.FitnessMetric.SPEED,
                Dependency.SPEED,
                88.0,
                true
        );

        ConnexxDataType partnerHRDataType = new ConnexxDataType(
                connexContext,
                "partner-hr",
                "Partner HR",
                "Displays partner heart rate and sends data to partner",
                PartnerService.FitnessMetric.HEART_RATE,
                Dependency.INTERVAL,
                99,
                false
        );

        ConnexxDataType partnerPowerDataType = new ConnexxDataType(
                connexContext,
                "partner-power",
                "Partner Power",
                "Displays partner power and sends data to partner",
                PartnerService.FitnessMetric.POWER,
                Dependency.INTERVAL,
                99.0,
                false
        );

        ConnexxDataType partnerSpeedDataType = new ConnexxDataType(
                connexContext,
                "partner-speed",
                "Partner Speed",
                "Displays partner speed and sends data to partner",
                PartnerService.FitnessMetric.SPEED,
                Dependency.INTERVAL,
                88.0,
                false
        );

        ConnexxDataType partnerFTPPercentageDataType = new ConnexxDataType(
                connexContext,
                "partner-%-of-FTP",
                "Partner % of FTP",
                "Displays partner % of FTP",
                PartnerService.FitnessMetric.POWER,
                Dependency.INTERVAL,
                99,
                false
        );
        partnerFTPPercentageDataType.setPercentageFormatter(PartnerService.FitnessMetric.FTP);

        ConnexxDataType partnerPercentageOfMaxHRDataType = new ConnexxDataType(
                connexContext,
                "partner-%-of-max-hr",
                "Partner % Of Max HR",
                "Displays partner % of max heart rate",
                PartnerService.FitnessMetric.HEART_RATE,
                Dependency.INTERVAL,
                99,
                false
        );
        partnerPercentageOfMaxHRDataType.setPercentageFormatter(PartnerService.FitnessMetric.MAX_HEART_RATE);

        CGMDataType cgmGlucoseDataType = new CGMDataType(getContext(), connexContext);

        android.util.Log.e("CGMDEBUG", "CGMDataType instantiated: " + cgmGlucoseDataType.getTypeId());
        android.util.Log.e("CGMDEBUG", "returning " + Arrays.asList(
                partnerHRDataType, partnerPowerDataType, partnerSpeedDataType,
                partnerFTPPercentageDataType, partnerPercentageOfMaxHRDataType,
                userHRDataType, userPowerDataType, userSpeedDataType,
                cgmGlucoseDataType).size() + " data types");

        return Arrays.asList(
                partnerHRDataType,
                partnerPowerDataType,
                partnerSpeedDataType,
                partnerFTPPercentageDataType,
                partnerPercentageOfMaxHRDataType,
                userHRDataType,
                userPowerDataType,
                userSpeedDataType,
                cgmGlucoseDataType
        );
    }
}