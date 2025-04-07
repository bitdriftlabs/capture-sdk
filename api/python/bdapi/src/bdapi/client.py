import struct
from datetime import datetime
from urllib import parse as _parse

import requests

from bdapi import models


class APIError(Exception):
    """Exception raised for errors in the Bitdrift API."""

    def __init__(self, message: str, status_code: int, grpc_status: int = -1, grpc_message: str = ""):
        super().__init__(message)
        self.status_code = status_code
        self.status = grpc_status
        self.message = grpc_message


class UnauthorizedError(Exception):
    """Exception raised for authentication errors with the Bitdrift API."""

    pass


def _connect_encode(payload: bytes) -> bytes:
    prefix = struct.pack(">BI", 0, len(payload))
    return prefix + payload


def _connect_decode(payload: bytes) -> bytes:
    # Unpack: [1-byte flag][4-byte len][payload]
    length = struct.unpack(">I", payload[1:5])[0]
    return payload[5 : 5 + length]


class Bitdrift:
    """
    A client for interacting with the public bitdrift API.

    This client provides convenient methods for communicating with the bitdrift API using
    Protocol Buffers over HTTP. It handles serialization, deserialization, and transport
    details so you can work with Python-native types and protobuf messages directly.

    Example:
        >>> from bitdrift.client import Bitdrift
        >>> client = Bitdrift(api_key="your-api-key")
        >>> client.ingest_metric(
        ...     metric_id="user_signup",
        ...     platform_type=MetricPlatform.APPLE,
        ...     app_id="com.example.app",
        ...     app_version="1.2.3",
        ...     counter_delta=1
        ... )

    Args:
        api_key (str): The API key for authenticating with the bitdrift API.
        base_url (str, optional): The base URL of the Bitdrift API (e.g., "https://api.bitdrift.io").
        timeout (float, optional): Timeout in seconds for HTTP requests. Defaults to 5.0.

    Notes:
        - All requests are sent using `application/connect+proto` content type.
        - Protobuf schemas are defined in `src/protos`.
        - This client targets bitdrift Public API v1.
    """

    def __init__(self, api_key: str, base_url: str = "https://api.bitdrift.io", timeout: float = 5.0):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.session.headers.update(
            {
                "x-loop-api-key": api_key,
                "Content-Type": "application/connect+proto",
            }
        )
        self.timeout = timeout

    def ingest_metric(
        self,
        *,
        metric_id: str,
        platform: models.MetricPlatform,
        app_id: str,
        app_version: str,
        timestamp: datetime | None = None,
        counter_delta: int,
    ) -> None:
        """
        Ingests a metric using Bitdrift's public API.

        Args:
            metric_id: The metric ID to which this data applies.
            platform_type: The originating platform (e.g., APPLE, ANDROID).
            app_id: The client application ID (e.g. io.foo.bar).
            app_version: The version of the app submitting the metric.
            timestamp: Optional timestamp to record against. If omitted, server time is used.
            counter_delta: Value to increment the counter by. Must be provided.

        Returns:
            An empty IngestMetricResponse on success.

        Raises:
            requests.HTTPError: If the response from the server is an error.
        """
        request = models._IngestMetricRequestDTO(
            metric_id=metric_id,
            platform_type=platform.value,
            app_id=app_id,
            app_version=app_version,
            counter_delta=counter_delta,
        )

        if timestamp:
            request.timestamp.FromDatetime(timestamp)

        url = f"{self.base_url}/bitdrift_public.protobuf.ingest.v1.IngestService/IngestMetric"

        resp = self.session.post(
            url,
            data=_connect_encode(request.SerializeToString()),
            timeout=self.timeout,
        )

        if resp.status_code == 401:
            raise UnauthorizedError("Unauthorized: Invalid API key")

        grpc_status = resp.headers.get("grpc-status", "0")
        grpc_message = _parse.unquote(resp.headers.get("grpc-message", ""))

        resp.raise_for_status()
        if grpc_status != "0":
            raise APIError(
                f"API Error: {grpc_message}",
                status_code=resp.status_code,
                grpc_status=int(grpc_status),
                grpc_message=grpc_message or "Unknown error",
            )

        if not resp.content:
            raise APIError("Empty response from server", status_code=resp.status_code)

        response = models._IngestMetricResponseDTO()
        response.ParseFromString(_connect_decode(resp.content))

    def close(self):
        self.session.close()
