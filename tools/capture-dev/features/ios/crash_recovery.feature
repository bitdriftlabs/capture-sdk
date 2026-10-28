@ios @physical-device
Feature: Crash recovery
  Scenario: A native crash becomes an uploadable Capture report
    Given the iOS sample app is installed on a physical device
    And I snapshot Capture's queued reports
    When I run the Maestro flow "ios-crash-now"
    Then the assertion "ios.crash_cache_exists" passes
    When I relaunch the iOS sample app
    Then the assertion "ios.new_crash_report" passes
