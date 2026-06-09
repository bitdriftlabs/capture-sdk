/// Represents an active span for distributed tracing.
///
/// End the span by calling [end] when the operation completes.
class Span {
  final String id;
  final String name;

  Span({required this.id, required this.name});

  @override
  String toString() => 'Span($name, id=$id)';
}
