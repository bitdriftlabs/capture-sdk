import unittest
from unittest.mock import patch, MagicMock
from datetime import datetime

from bdapi.client import APIError, UnauthorizedError, Bitdrift
from bdapi.models import _IngestMetricResponseDTO, MetricPlatform


class TestBitdriftAPIClient(unittest.TestCase):
    def setUp(self):
        self.api_key = "test-api-key"
        self.client = Bitdrift(api_key=self.api_key)

    def tearDown(self):
        self.client.close()

    @patch("bdapi.client.requests.Session.post")
    def test_ingest_metric_unauthorized(self, mock_post):
        fake_response = MagicMock()
        fake_response.content = _IngestMetricResponseDTO().SerializeToString()
        fake_response.status_code = 401
        mock_post.return_value = fake_response

        # Assertions
        with self.assertRaises(UnauthorizedError):
            self.client.ingest_metric(
                metric_id="signup_event",
                platform=MetricPlatform.APPLE,
                app_id="com.example.app",
                app_version="1.0.0",
                timestamp=datetime.now(),
                counter_delta=42,
            )
        mock_post.assert_called_once()

    @patch("bdapi.client.requests.Session.post")
    def test_ingest_metric_failure(self, mock_post):
        fake_response = MagicMock()
        fake_response.content = _IngestMetricResponseDTO().SerializeToString()
        fake_response.status_code = 200
        fake_response.raise_for_status = MagicMock()
        fake_response.headers = {"grpc-status": "3", "grpc-message": "Some Error"}
        mock_post.return_value = fake_response

        # Assertions
        with self.assertRaises(APIError) as error:
            self.client.ingest_metric(
                metric_id="signup_event",
                platform=MetricPlatform.APPLE,
                app_id="com.example.app",
                app_version="1.0.0",
                timestamp=datetime.now(),
                counter_delta=42,
            )

        mock_post.assert_called_once()
        self.assertEqual(error.exception.status_code, 200)
        self.assertEqual(error.exception.status, 3)
        self.assertEqual(error.exception.message, "Some Error")
        self.assertEqual(self.client.session.headers["x-loop-api-key"], self.api_key)

    @patch("bdapi.client.requests.Session.post")
    def test_ingest_metric_success(self, mock_post):
        fake_response = MagicMock()
        fake_response.content = _IngestMetricResponseDTO().SerializeToString()
        fake_response.raise_for_status = MagicMock()
        fake_response.headers = {"grpc-status": "0"}
        mock_post.return_value = fake_response

        self.client.ingest_metric(
            metric_id="signup_event",
            platform=MetricPlatform.APPLE,
            app_id="com.example.app",
            app_version="1.0.0",
            timestamp=datetime.now(),
            counter_delta=42,
        )

        # Assertions
        mock_post.assert_called_once()
        self.assertEqual(self.client.session.headers["x-loop-api-key"], self.api_key)
