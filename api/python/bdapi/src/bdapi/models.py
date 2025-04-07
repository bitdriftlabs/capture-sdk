from enum import Enum

from protos.bitdrift_public.protobuf.ingest.v1 import api_pb2 as _pb

_IngestMetricRequestDTO = _pb.IngestMetricRequest
_IngestMetricResponseDTO = _pb.IngestMetricResponse
_MetricPlatformTypeDTO = _pb.MetricPlatformType

# These are just internal interfaces
_ = (_IngestMetricRequestDTO, _IngestMetricResponseDTO, _MetricPlatformTypeDTO)


class MetricPlatform(Enum):
    APPLE = _pb.MetricPlatformType.APPLE
    ANDROID = _pb.MetricPlatformType.ANDROID
    ELECTRON = _pb.MetricPlatformType.ELECTRON


__all__ = ["MetricPlatform"]
