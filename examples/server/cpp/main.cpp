#include "src/lib.rs.h"

#include <iostream>
#include <chrono>
#include <thread>

using namespace std;
using namespace rust;

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
        logger->log(1, "Hello, World!", Vec<LogField>());
        this_thread::sleep_for(std::chrono::seconds(5));
    }

    return 0;
}
