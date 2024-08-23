#pragma once

#import <Foundation/Foundation.h>
#import "CaptureTestBridging-Swift.h"
#include "CapturePassable-Swift.h"
#include <stdint.h>

@class Field;

typedef int32_t stream_id;
typedef int64_t logger_id;

// C interfaces for functions defined in the Rust test helpers. Refer to the implementation for
// function documentation.

int32_t start_test_api_server(bool tls, int32_t ping_interval);

void stop_test_api_server();

// Returns the ID of the next API stream that's opened (if any).
// Timeouts after predefined amount of time if no stream is open.
stream_id await_next_api_stream();

void await_api_server_received_handshake(stream_id stream_id);

// Await a configuration ack message from a connected peer.
void await_configuration_ack(stream_id);

void configure_benchmarking_configuration(stream_id stream_id);

void configure_benchmarking_configuration_with_workflows(stream_id stream_id);

void next_test_api_stream(ContinuationWrapper *continuation);

void test_stream_received_handshake(stream_id stream_id, ContinuationWrapper *continuation);

void test_stream_closed(stream_id stream_id, uint64_t wait_time_ms,
                        ContinuationWrapper *continuation);

void configure_aggressive_continuous_uploads(stream_id stream_id);

/// Stores SDK benchmarking configuration in a given directory.
void create_benchmarking_configuration(const char *dir_path);

bool next_uploaded_log(UploadedLog *uploaded_log);

void run_aggressive_upload_test(logger_id logger_id);

void run_large_upload_test(logger_id logger_id);

void run_aggressive_upload_test_with_stream_drops(logger_id logger_id);

void test_null_termination(const void *object);

void run_key_value_storage_test();

void run_resource_utilization_target_test(id<ResourceUtilizationTarget>);

void run_events_listener_target_test(id<EventsListenerTarget>);
