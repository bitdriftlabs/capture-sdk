// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp;

import io.bitdrift.capture.Capture;
import io.bitdrift.capture.Configuration;
import io.bitdrift.capture.providers.FieldProvider;
import io.bitdrift.capture.providers.session.SessionStrategy;
import io.bitdrift.gradletestapp.ConfigurationSettingsFragment.SessionStrategyPreferences;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.HttpUrl;

public class BitdriftInit {
    public static void initBitdriftCaptureInJava(
            HttpUrl apiUrl,
            String apiKey,
            String sessionStrategyName,
            Configuration configuration) {
        String userID = UUID.randomUUID().toString();
        List<FieldProvider> fieldProviders = new ArrayList<>();
        fieldProviders.add(() -> {
            Map<String, String> fields = new Hashtable<>();
            fields.put("user_id", userID);
            return fields;
        });

        Capture.Logger.start(
            apiKey,
            mapToSessionStrategy(sessionStrategyName),
            configuration,
            fieldProviders,
            null,
            apiUrl
        );
    }

    private static SessionStrategy mapToSessionStrategy(String sessionStrategyName) {
        if(sessionStrategyName.equals(SessionStrategyPreferences.ACTIVITY_BASED.getDisplayName())){
            return new SessionStrategy.ActivityBased();
        }else{
            return new SessionStrategy.Fixed();
        }
    }
}
