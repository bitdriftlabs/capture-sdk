    cmake_minimum_required(VERSION 3.6.0)
    project(native-lib)

    add_library(
            native-lib
            SHARED
            native.cpp)

    find_library(
            log-lib
            log)

    target_link_libraries(
            native-lib
            ${log-lib})
