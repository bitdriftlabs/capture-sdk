// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

@_spi(ExperimentalAPI) public class UserSettingsCommands {
    /// Remove a value from UserDefaults
    ///
    /// - parameter arguments: command arguments. requires `key` (string)
    ///
    /// - returns: an empty `CommandResult` or an error
    static func defaultsRemove(_ arguments: CommandArguments) -> Result<CommandResult, CommandError> {
        do {
            let defaults = try getDefaults(arguments)
            let key = try getKey(arguments)
            defaults.removeObject(forKey: key)
            return .success(CommandResult())
        } catch let error as CommandError {
            return .failure(error)
        } catch {
            return .failure(CommandError(message: "could not complete command"))
        }
    }

    /// Add a value to UserDefaults
    ///
    /// - parameter arguments: command arguments. requires `key` (string) and `value` (any)
    ///
    /// - returns: an empty `CommandResult` or an error
    static func defaultsSet(_ arguments: CommandArguments) -> Result<CommandResult, CommandError> {
        do {
            let defaults = try getDefaults(arguments)
            let key = try getKey(arguments)
            guard let value = arguments["value"] else {
                throw CommandError.invalidArguments(message: "must specify <value> and string <key> to set")
            }
            defaults.set(value.value(), forKey: key)
            return .success(CommandResult())
        } catch let error as CommandError {
            return .failure(error)
        } catch {
            return .failure(CommandError(message: "could not complete command"))
        }
    }

    /// Retrieve a value from UserDefaults
    ///
    /// - parameter arguments: command arguments. requires `key` (string)
    ///
    /// - returns: a `CommandResult` containing the desired key/value in context or an error
    static func defaultsGet(_ arguments: CommandArguments) -> Result<CommandResult, CommandError> {
        do {
            let defaults = try getDefaults(arguments)
            let key = try getKey(arguments)
            guard let value = defaults.value(forKey: key) else {
                return .failure(CommandError(message: "Unable to fetch value: key '\(key)' is not set"))
            }
            return .success(CommandResult(context: [key: "\(value)"]))
        } catch let error as CommandError {
            return .failure(error)
        } catch {
            return .failure(CommandError(message: "could not complete command"))
        }
    }

    /// Clear all values from UserDefaults
    ///
    /// - parameter arguments: command arguments (unused)
    ///
    /// - returns: an empty `CommandResult` or an error
    static func defaultsClear(_ arguments: CommandArguments) -> Result<CommandResult, CommandError> {
        if let suiteName = arguments["suite_name"], case .string(let suiteName) = suiteName {
            UserDefaults.standard.removePersistentDomain(forName: suiteName)
            return .success(CommandResult())
        }

        if let domain = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: domain)
        } else {
            for (key, _) in UserDefaults.standard.dictionaryRepresentation() {
                UserDefaults.standard.removeObject(forKey: key)
            }
        }
        return .success(CommandResult())
    }

    private static func getKey(_ arguments: CommandArguments) throws -> String {
        guard let keyArg = arguments["key"], case .string(let key) = keyArg else {
            throw CommandError.invalidArguments(message: "must specify string <key>")
        }
        return key
    }

    private static func getDefaults(_ arguments: CommandArguments) throws -> UserDefaults {
        guard let suiteName = arguments["suite_name"] else {
            return UserDefaults.standard
        }
        guard case .string(let suiteName) = suiteName else {
            throw CommandError.invalidArguments(message: "must specify string <suite_name>")
        }
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            throw CommandError(message: "unable to fetch UserDefaults: unable to read suite named \(suiteName)")
        }
        return defaults
    }
}
