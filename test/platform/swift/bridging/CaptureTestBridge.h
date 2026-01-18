#pragma once

#import <Foundation/Foundation.h>
#import "CaptureTestBridging-Swift.h"
#include "CapturePassable-Swift.h"
#include <stdint.h>

@class Field;

typedef int32_t stream_id;
typedef int64_t logger_id;
typedef void* test_server_handle;

void* create_test_api_server_instance(bool tls, int32_t ping_interval);
int32_t server_instance_port(test_server_handle handle);
void destroy_test_api_server_instance(test_server_handle handle);
stream_id server_instance_await_next_stream(test_server_handle handle);
void server_instance_wait_for_handshake(test_server_handle handle, stream_id stream_id);
void server_instance_await_handshake(test_server_handle handle, stream_id stream_id);
bool server_instance_await_stream_closed(test_server_handle handle, stream_id stream_id, int64_t wait_time_ms);
void server_instance_send_configuration(test_server_handle handle, stream_id stream_id);
void server_instance_await_configuration_ack(test_server_handle handle, stream_id stream_id);
void server_instance_configure_aggressive_uploads(test_server_handle handle, stream_id stream_id);
void server_instance_run_aggressive_upload_test(test_server_handle handle, logger_id logger_id);
bool server_instance_run_large_upload_test(test_server_handle handle, logger_id logger_id);
bool server_instance_run_aggressive_upload_with_stream_drops(test_server_handle handle, logger_id logger_id);
bool server_instance_next_uploaded_log(test_server_handle handle, UploadedLog *uploaded_log);

/// Stores SDK benchmarking configuration in a given directory.
void create_benchmarking_configuration(const char *dir_path);

void test_null_termination(const void *object);

void run_key_value_storage_test();

void run_resource_utilization_target_test(id<ResourceUtilizationTarget>);

void run_session_replay_target_test(id<SessionReplayTarget>);

void run_events_listener_target_test(id<EventsListenerTarget>);
