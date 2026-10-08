/// Operation mode of the logger. Sleep mode reduces SDK activity to a minimum.
enum SleepMode { enabled, disabled }

/// Information about how the previous app run ended.
class PreviousRunInfo {
  /// Whether the previous run ended in a fatal termination.
  final bool hasFatallyTerminated;

  /// Platform specific termination reason, when available.
  final String? terminationReason;

  const PreviousRunInfo({
    required this.hasFatallyTerminated,
    this.terminationReason,
  });

  factory PreviousRunInfo.fromMap(Map<String, dynamic> map) => PreviousRunInfo(
    hasFatallyTerminated: map['hasFatallyTerminated'] as bool? ?? false,
    terminationReason: map['terminationReason'] as String?,
  );

  @override
  String toString() =>
      'PreviousRunInfo(hasFatallyTerminated: $hasFatallyTerminated, '
      'terminationReason: $terminationReason)';
}
