// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "src/lib.rs.h"

#include <iostream>
#include <chrono>
#include <thread>

using namespace std;
using namespace bitdrift;

int main(){
    auto logger = new_logger(
        "<API KEY>",
        "https://api.bitdrift.dev",
        "/tmp/storage",
        "hello-world",
        "0.0.1",
        "server",
        "0.0",
        "en_US"
    );
    cout << "Device ID: " << logger->device_id().c_str() << endl;

    for (int i = 0; i < 100; i++) {
        rust::Vec<LogField> fields = {
            LogField { "key1", "value1" },
            LogField { "key2", "value2" },
            LogField { "key3", "value3" }
        };
        logger->log(1, "Hello, World!", fields);
        this_thread::sleep_for(std::chrono::seconds(5));
    }

    return 0;
}
