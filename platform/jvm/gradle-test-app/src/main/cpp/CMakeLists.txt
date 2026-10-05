cmake_minimum_required(VERSION 3.22.1)
project(gradletestapp_native C)

add_library(gradletestapp_native SHARED native_threads.c)
target_link_libraries(gradletestapp_native log)
target_link_options(gradletestapp_native PRIVATE "-Wl,-z,max-page-size=16384")
