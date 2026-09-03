import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:capture_flutter/capture_flutter.dart';

void main() {
  runApp(const CaptureExampleApp());
}

class CaptureExampleApp extends StatelessWidget {
  const CaptureExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Capture SDK Example',
      theme: ThemeData(colorSchemeSeed: Colors.blue, useMaterial3: true),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  String _status = 'Not initialized';
  String? _sessionId;
  String? _apiKey;
  String _apiUrl = 'https://api.bitdrift.io';

  @override
  void initState() {
    super.initState();
    _loadConfigAndStart();
  }

  Future<void> _loadConfigAndStart() async {
    final prefs = await SharedPreferences.getInstance();
    _apiKey = prefs.getString('capture_api_key');
    _apiUrl = prefs.getString('capture_api_url') ?? 'https://api.bitdrift.io';

    if (_apiKey == null || _apiKey!.isEmpty) {
      setState(
        () => _status = 'Not configured — tap settings to enter API key',
      );
      return;
    }

    await _startCapture();
  }

  Future<void> _startCapture() async {
    try {
      final success = await Capture.start(
        apiKey: _apiKey!,
        apiUrl: _apiUrl,
        enableSessionReplay: true,
      );
      final sessionId = await Capture.sessionId;
      setState(() {
        _status = success ? 'Started successfully' : 'Start failed';
        _sessionId = sessionId;
      });
    } catch (e) {
      setState(() => _status = 'Error: $e');
    }
  }

  Future<void> _openSettings() async {
    final result = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => SettingsPage(apiKey: _apiKey ?? '', apiUrl: _apiUrl),
      ),
    );
    if (result == true) {
      await _loadConfigAndStart();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Capture SDK Example'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: _openSettings,
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Status: $_status'),
            if (_sessionId != null) Text('Session ID: $_sessionId'),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () =>
                  Capture.logInfo('Button tapped', fields: {'screen': 'home'}),
              child: const Text('Log Info'),
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: () => Capture.logWarning('Warning test'),
              child: const Text('Log Warning'),
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: () {
                Capture.logError('Error test');
                showDialog(
                  context: context,
                  builder: (ctx) => AlertDialog(
                    title: const Text('Error Logged'),
                    content: const Text('An error event was sent to Capture.'),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.of(ctx).pop(),
                        child: const Text('OK'),
                      ),
                    ],
                  ),
                );
              },
              child: const Text('Log Error'),
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: () async {
                await Capture.startNewSession();
                final id = await Capture.sessionId;
                setState(() => _sessionId = id);
              },
              child: const Text('New Session'),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: () async {
                final url = await Capture.sessionUrl;
                if (url != null) {
                  await Clipboard.setData(ClipboardData(text: url));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Session URL copied!')),
                    );
                  }
                }
              },
              icon: const Icon(Icons.copy),
              label: const Text('Copy Session URL'),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: () {
                // Force a crash to test crash reporting
                throw StateError('Test crash from Flutter');
              },
              icon: const Icon(Icons.warning),
              label: const Text('Crash App'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: () async {
                final status = await Capture.getSdkStatus();
                final deviceId = await Capture.deviceId;
                if (context.mounted) {
                  showDialog(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: const Text('SDK Status'),
                      content: Text(
                        'State: ${status?['initializationState'] ?? 'unknown'}\n'
                        'Device ID: ${deviceId ?? 'n/a'}\n'
                        'Last Handshake: ${status?['lastHandshakeTimeMs'] ?? status?['lastHandshakeTime'] ?? 'never'}\n'
                        'Last Config: ${status?['lastConfigDeliveryTimeMs'] ?? status?['lastConfigDeliveryTime'] ?? 'never'}',
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.of(ctx).pop(),
                          child: const Text('OK'),
                        ),
                      ],
                    ),
                  );
                }
              },
              icon: const Icon(Icons.info_outline),
              label: const Text('SDK Status'),
            ),
          ],
        ),
      ),
    );
  }
}

class SettingsPage extends StatefulWidget {
  final String apiKey;
  final String apiUrl;

  const SettingsPage({super.key, required this.apiKey, required this.apiUrl});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late final TextEditingController _apiKeyController;
  late final TextEditingController _apiUrlController;

  @override
  void initState() {
    super.initState();
    _apiKeyController = TextEditingController(text: widget.apiKey);
    _apiUrlController = TextEditingController(text: widget.apiUrl);
  }

  @override
  void dispose() {
    _apiKeyController.dispose();
    _apiUrlController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('capture_api_key', _apiKeyController.text.trim());
    await prefs.setString('capture_api_url', _apiUrlController.text.trim());
    if (mounted) {
      Navigator.of(context).pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _apiKeyController,
              decoration: const InputDecoration(
                labelText: 'API Key',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _apiUrlController,
              decoration: const InputDecoration(
                labelText: 'API URL',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _save,
              child: const Text('Save & Restart SDK'),
            ),
          ],
        ),
      ),
    );
  }
}
