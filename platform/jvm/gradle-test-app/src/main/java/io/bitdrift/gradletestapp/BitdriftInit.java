// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp;

import io.bitdrift.capture.Capture;
import io.bitdrift.capture.Configuration;
import io.bitdrift.capture.LoggerStateListener;
import io.bitdrift.capture.providers.FieldProvider;
import io.bitdrift.capture.providers.session.SessionStrategy;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.HttpUrl;

public class BitdriftInit {
    public static void initBitdriftCaptureInJava(
            HttpUrl apiUrl, String apiKey, LoggerStateListener loggerStateListener) {
        String userID = UUID.randomUUID().toString();
        List<FieldProvider> fieldProviders = new ArrayList<>();
        fieldProviders.add(() -> {
            Map<String, String> fields = new Hashtable<>();
            fields.put("user_id", userID);
            return fields;
        });

        Capture.Logger.start(
            apiKey,
            new SessionStrategy.ActivityBased(),
            new Configuration(),
            fieldProviders,
            null,
            apiUrl,
            loggerStateListener
        );
    }
}
