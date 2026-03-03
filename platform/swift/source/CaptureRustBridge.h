#pragma once

#import <Foundation/Foundation.h>
#include <Foundation/NSArray.h>
#include <Foundation/NSData.h>
#include <Foundation/NSString.h>
#include <Foundation/NSDictionary.h>
#include "CapturePassable-Swift.h"
#include <stdint.h>

typedef int64_t logger_id;
typedef uintptr_t stream_id;

NS_ASSUME_NONNULL_BEGIN

/*
 * Reports an error to the bitdrift backend and log it to the console. Both reporting to remote and logging
 * to the console are throttled to protect against noisy errors.
 */
void capture_report_error(const char *message);

/*
 * Creates a new logger.
 *
 * @param path the path to the SDK directory used by the logger for disk persistence.
 * @param api_key the key used to authenticate the application with bitdrift services.
 * @param session_strategy_provider the session strategy provider.
 * @param metadata_provider used to provide the internal logger with logging metadata.
 * @param resource_utilization_target responsible for emitting resource utilization logs in response to provided ticks.
 * @param session_replay_target responsible for emitting session replay logs in response to callbacks.
 * @param events_listener_target responsible for listening to platform events and emitting logs in response to them.
 * @param app_id the app id to identify the client as a null terminated C string.
 * @param app_version the app version to identify the client as a null terminated C string.
 * @param model the model of the device to identify the client as a null terminated C string.
 * @param network the Capture Network protocol to use for performing network requests.
 * @param error_reporter the error reported protocol to use for reporting errors.
 * @param start_in_sleep_mode true if sleep mode should initialize now
 */
logger_id capture_create_logger(
    const char *_Nullable path,
    const char *api_key,
    id<SessionStrategyProvider> session_strategy_provider,
    id<MetadataProvider> metadata_provider,
    id<ResourceUtilizationTarget> resource_utilization_target,
    id<SessionReplayTarget> session_replay_target,
    id<EventsListenerTarget> events_listener_target,
    const char *app_id,
    const char *app_version,
    const char *model,
    _Nullable id<Network> network,
    _Nullable id<RemoteErrorReporting> error_reporter,
    bool start_in_sleep_mode
);

/*
 * Starts the logger. This must be called exactly once before any logs are written to the logger.
 *
 * @param the logger to start.
 */
void capture_start_logger(logger_id logger_id);

/*
 * Writes a single log line.
 *
 * All the data provided here must be valid only and remain unchanged for the duration of the
 * function call.
 *
 * @param logger_id the logger to write to.
 * @param log_level the log level.
 * @param log_type the type of log (e.g. normal, session replay, resource monitoring, etc).
 * @param message the log message to write.
 * @param fields The list of fields which the SDK matches on, potentially stores, and uploads to
 *        remote services.
 * @param matching_fields The list of matching fields that can be read when processing a given log but are
 *        not a part of the log itself.
 * @Param blocking whether the method should return only after the log is processed.
 */
void capture_write_log(
    logger_id logger_id,
    int32_t log_level,
    uint32_t log_type,
    const char *message,
    const NSArray<const Field *> *_Nullable fields,
    const NSArray<const Field *> *_Nullable matching_fields,
    bool blocking,
    int64_t occurred_at_override
);

/*
 * Writes a session replay screen log.
 *
 * @param logger_id the ID of the logger to write to.
 * @param fields the fields to include with the log.
 * @param duration_s the duration of time the preparation of the session replay log took.
 */
void capture_write_session_replay_screen_log(
    logger_id logger_id,
    const NSArray<const Field *> *fields,
    double duration_s
);

/*
 * Writes a session replay screenshot log.
 *
 * @param logger_id the ID of the logger to write to.
 * @param fields the fields to include with the log.
 * @param duration_s the duration of time the preparation of the session replay log took.
 */
void capture_write_session_replay_screenshot_log(
    logger_id logger_id,
    const NSArray<const Field *> *fields,
    double duration_s
);

/*
 * Writes a resource utilization log.
 *
 * @param logger_id the ID of the logger to write to.
 * @param fields the fields to include with the log.
 * @param duration_s the duration of time the preparation of the resource log took.
 */
void capture_write_resource_utilization_log(
    logger_id logger_id,
    const NSArray<const Field *> *fields,
    double duration_s
);

/*
 * Writes an SDK started log.
 *
 * @param logger_id the ID of the logger to write to.
 * @param fields the fields to include with the log.
 * @param duration_s the duration of time the SDK configuration took.
 */
void capture_write_sdk_start_log(
    logger_id logger_id,
    const NSArray<const Field *> *fields,
    double duration_s
);

/*
 * Checks whether the app update log should be written.
 *
 * @param loggerId the ID of the logger to use.
 * @param app_version the version of the app.
 * @param build_number the app build number.
 */
bool capture_should_write_app_update_log(
    logger_id logger_id,
    NSString *app_version,
    NSString *build_number
);

/*
 * Writes an app update log.
 *
 * @param loggerId the ID of the logger to write to.
 * @param app_version the version of the app.
 * @param build_number the app build number.
 * @param app_install_size_bytes the size of the app in bytes.
 * @param duration_s the duration of time the preparation of the log took.
 */
void capture_write_app_update_log(
    logger_id logger_id,
    NSString *app_version,
    NSString *build_number,
    uint64_t app_install_size_bytes,
    double duration_s
);

/*
 * Writes an app launch TTI log. The method should be called only once per logger Id. Consecutive calls
 * have no effect.
 *
 * @param loggerId the ID of the logger to write to.
 * @param duration_s the duration of time between a user's intent to launch an app and the point in time
 *        when the app became interactive. Calls with a negative duration are ignored.
 */
void capture_write_app_launch_tti_log(
    logger_id logger_id,
    double duration_s
);

/*
* Writes a screen view log.
*
* @param loggerId the ID of the logger to write to.
* @param screen_name the name of the screen.
*/
void capture_write_screen_view_log(
    logger_id logger_id,
    NSString *screen_name
);

/*
 * Starts new sessions using configured session strategy.
 *
 * @param logger_id the logger to use.
 */
void capture_start_new_session(logger_id logger_id);

/*
 * Returns currently active session id.
 *
 * @param logger_id the logger to use.
 */
NSString *capture_get_session_id(logger_id logger_id);

/*
 * Returns the device ID. The ID is generated the first time it is accessed. Consecutive calls to this
 * method return the same value.
 *
 * @param logger_id the logger to use.
 */
NSString *capture_get_device_id(logger_id logger_id);

/**
 * Returns the version of the Capture SDK
 */
NSString *capture_get_sdk_version();

/*
 * Adds a field that should be attached to all logs emitted by the logger going forward.
 * If a field with a given key has already been registered with the logger, its value is
 * overridden with the new value.
 *
 * Fields added with this method take precedence over fields returned by registered `FieldProvider`s
 * and are overwritten by custom logs emitted.
 *
 * @param logger_id the logger to add the field to.
 * @param key the name of the field.
 * @param value the value of the field.
 */
void capture_add_log_field(logger_id logger_id, const char *key, const char *value);

/*
 * Removes a field with a given key. This operation does nothing if the field with the given key is not
 * registered with the logger.
 *
 * @param logger_id the logger to remove the field from.
 * @param key the name of the field to remove.
 */
void capture_remove_log_field(logger_id logger_id, const char *key);

/*
 * Flushes logger's state to disk.
 *
 * @param logger_id the logger to flush.
 * @param blocking whether the method should return only after the flush is complete.
 * @param poll_callback optional callback invoked during blocking wait instead of sleeping.
 *        This allows the caller to pump the run loop while waiting.
 */
void capture_flush(logger_id logger_id, bool blocking, void (*_Nullable poll_callback)(void));

/*
 * Sets a feature flag exposure.
 *
 * @param logger_id the logger to set the feature flag exposure on.
 * @param flag the flag to set.
 * @param variant the variant to set (if null, the flag will be set with no variant).
 */
void capture_set_feature_flag_exposure(logger_id logger_id, const char *flag,
                                       const char *_Nullable variant);

/**
 * Signals the specified logger to shut down.
 *
 * @param blocking whether the method should return only after shutdown is complete.
 */
void capture_shutdown_logger(logger_id logger_id, bool blocking);

/*
 * Passes the received data as a pointer to a buffer of bytes along with the number of bytes.
 */
void capture_api_received_data(stream_id stream_id, const uint8_t *data, size_t size);

/*
 * Closes the stream with the provided reason. This can safely be called multiple times; repeat
 * calls will have no effect.
 */
void capture_api_stream_closed(stream_id stream_id, const char *reason);

/*
 * Called to release memory associated with a stream. This should be called when the Swift side
 * is done with the stream. after it has been closed. This must be called exactly once.
 */
void capture_api_release_stream(stream_id);

/*
 * Checks whether a bool runtime variable is enabled via client runtime configuration.
 *
 * @param logger_id the logger to check the variable value for.
 * @param variable_name the name of the feature to check.
 * @param default_value the default value to use when the relevant configuration entry is missing.
 *
 * @returns the value of the bool variable.
 */
bool capture_runtime_bool_variable_value(logger_id logger_id, const char *variable_name, bool default_value);

/*
 * Returns the value of an integer runtime variable via client runtime configuration.
 *
 * @param logger_id the logger to check the variable value for.
 * @param variable_name the name of the int variable to check.
 * @param default_value the default value to use when the relevant configuration entry is missing.
 *
 * @returns the value of the uin32_t variable.
 */
uint32_t capture_runtime_uint32_variable_value(logger_id logger_id, const char *variable_name, uint32_t default_value);

/*
 * Records the session replay record capture screen duration.
 *
 * @param logger_id the logger to record the metric for.
 * @param duration_s the screen capture duration expressed in seconds.
 */
void capture_session_replay_record_capture_screen_duration(logger_id logger_id, double duration_s);

/*
 * Normalizes a URL path by replacing high cardinality substrings with `<id>` placeholder.
 *
 * @param url_path the URL path to normalize.
 */
NSString * capture_normalize_url_path(const char *url_path);

/**
 * Set sleep mode active or inactive
 *
 * @param logger_id the logger to set sleep mode for
 * @param enabled true if sleep mode should be active
 */
void capture_set_sleep_mode(logger_id logger_id, bool enabled);

/**
 * Process pending issue reports
 *
 * @param logger_id the logger to use in recording
 * @param session_value 0 for Current, 1 for PreviousRun
 */
void capture_process_issue_reports(logger_id logger_id, int session_value);

/**
 * Persist a JavaScript error report
 *
 * @param logger_id the logger to use
 * @param error_name the name of the error
 * @param error_message the error message
 * @param stack_trace the JavaScript stack trace
 * @param is_fatal whether this is a fatal error
 * @param engine the JavaScript engine name (e.g., "hermes", "jsc")
 * @param debug_id optional debug ID for source map resolution
 * @param timestamp_seconds timestamp seconds since Unix epoch
 * @param timestamp_nanos nanoseconds component of the timestamp (0-999,999,999)
 * @param destination_path the file path where the report should be written
 * @param manufacturer device manufacturer (e.g., "Apple")
 * @param model device model identifier
 * @param os_version operating system version
 * @param os_brand operating system brand (e.g., "iOS")
 * @param app_id application bundle identifier
 * @param app_version application version string
 * @param version_code application version code/build number
 * @param sdk_version bitdrift's React Native SDK version(e.g 8.1)
 */
void capture_persist_javascript_error_report(
    const char *error_name,
    const char *error_message,
    const char *stack_trace,
    bool is_fatal,
    const char *engine,
    const char *_Nullable debug_id,
    uint64_t timestamp_seconds,
    uint32_t timestamp_nanos,
    const char *destination_path,
    const char *manufacturer,
    const char *model,
    const char *os_version,
    const char *os_brand,
    const char *app_id,
    const char *app_version,
    const char *version_code,
    const char *sdk_version);

NS_ASSUME_NONNULL_END
