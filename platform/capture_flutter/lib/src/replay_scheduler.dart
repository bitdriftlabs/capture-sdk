import 'dart:async';

/// Decides when to capture a replay screen from a stream of rendered frames.
///
/// A capture happens once frames stop for [settleDelay], so transitions are
/// recorded in their final state. Continuous animations are still captured
/// at least every [maxDelay], and captures are at least [minInterval] apart.
class ReplayCaptureScheduler {
  ReplayCaptureScheduler(
    this.onCapture, {
    this.settleDelay = const Duration(milliseconds: 150),
    this.maxDelay = const Duration(seconds: 1),
    this.minInterval = const Duration(milliseconds: 500),
  });

  final void Function() onCapture;
  final Duration settleDelay;
  final Duration maxDelay;
  final Duration minInterval;

  Timer? _settle;
  Timer? _deadline;
  Timer? _cooldown;
  bool _framesDuringCooldown = false;

  /// Notifies the scheduler that a frame was rendered.
  void onFrame() {
    if (_cooldown != null) {
      _framesDuringCooldown = true;
      return;
    }
    _settle?.cancel();
    _settle = Timer(settleDelay, _capture);
    _deadline ??= Timer(maxDelay, _capture);
  }

  void cancel() {
    _settle?.cancel();
    _deadline?.cancel();
    _cooldown?.cancel();
    _settle = null;
    _deadline = null;
    _cooldown = null;
    _framesDuringCooldown = false;
  }

  void _capture() {
    _settle?.cancel();
    _deadline?.cancel();
    _settle = null;
    _deadline = null;
    onCapture();
    _cooldown = Timer(minInterval, () {
      _cooldown = null;
      if (_framesDuringCooldown) {
        _framesDuringCooldown = false;
        onFrame();
      }
    });
  }
}
