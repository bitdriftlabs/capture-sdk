# 🛰️ bdapi – bitdrift's public API client (Python)

A modern, typed Python client for [bitdrift](https://bitdrift.io)'s public API.

## ✨ Supported APIs

- ✅ Ingestion for server-side funnels

## 🚀 Installation

```bash
pip install bdapi
```

Or if you're using Poetry:

```bash
poetry add bdapi
```

## 🔧 Usage

```python
from bdapi import Bitdrift, MetricPlatform
from datetime import datetime

client = Bitdrift(api_key="your-api-key")

client.ingest_metric(
    metric_id="signup_event",
    platform=MetricPlatform.ANDROID,
    app_id="com.example.app",
    app_version="1.0.0",
    timestamp=datetime.utcnow(),
    counter_delta=1,
)
```

## 📦 API Overview

| Method           | Description                        |
|------------------|------------------------------------|
| `ingest_metric()`| Send a counter-based metric to Bitdrift |

All requests are sent to `https://api.bitdrift.io` via `POST` using the Connect protocol and `application/connect+proto`.

## 🧪 Running Tests

```bash
poetry install
poetry run pytest
```

## 🔒 Authentication

All API calls require the `x-loop-api-key` header. Pass your key to the client:

```python
Bitdrift(api_key="...")
```

## 📖 Resources

- [Bitdrift Docs](https://bitdrift.io/docs)
