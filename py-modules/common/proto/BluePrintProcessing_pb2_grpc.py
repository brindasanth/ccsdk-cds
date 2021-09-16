# Generated by the gRPC Python protocol compiler plugin. DO NOT EDIT!
"""Client and server classes corresponding to protobuf-defined services."""
import grpc

import proto.BluePrintProcessing_pb2 as BluePrintProcessing__pb2


class BluePrintProcessingServiceStub(object):
  """Missing associated documentation comment in .proto file."""

  def __init__(self, channel):
    """Constructor.

    Args:
        channel: A grpc.Channel.
    """
    self.process = channel.stream_stream(
        '/org.onap.ccsdk.cds.controllerblueprints.processing.api.BluePrintProcessingService/process',
        request_serializer=BluePrintProcessing__pb2.ExecutionServiceInput.SerializeToString,
        response_deserializer=BluePrintProcessing__pb2.ExecutionServiceOutput.FromString,
    )


class BluePrintProcessingServiceServicer(object):
  """Missing associated documentation comment in .proto file."""

  def process(self, request_iterator, context):
    """Missing associated documentation comment in .proto file."""
    context.set_code(grpc.StatusCode.UNIMPLEMENTED)
    context.set_details('Method not implemented!')
    raise NotImplementedError('Method not implemented!')


def add_BluePrintProcessingServiceServicer_to_server(servicer, server):
  rpc_method_handlers = {
    'process': grpc.stream_stream_rpc_method_handler(
        servicer.process,
        request_deserializer=BluePrintProcessing__pb2.ExecutionServiceInput.FromString,
        response_serializer=BluePrintProcessing__pb2.ExecutionServiceOutput.SerializeToString,
    ),
  }
  generic_handler = grpc.method_handlers_generic_handler(
      'org.onap.ccsdk.cds.controllerblueprints.processing.api.BluePrintProcessingService', rpc_method_handlers)
  server.add_generic_rpc_handlers((generic_handler,))


# This class is part of an EXPERIMENTAL API.
class BluePrintProcessingService(object):
  """Missing associated documentation comment in .proto file."""

  @staticmethod
  def process(request_iterator,
      target,
      options=(),
      channel_credentials=None,
      call_credentials=None,
      insecure=False,
      compression=None,
      wait_for_ready=None,
      timeout=None,
      metadata=None):
    return grpc.experimental.stream_stream(request_iterator, target, '/org.onap.ccsdk.cds.controllerblueprints.processing.api.BluePrintProcessingService/process',
                                           BluePrintProcessing__pb2.ExecutionServiceInput.SerializeToString,
                                           BluePrintProcessing__pb2.ExecutionServiceOutput.FromString,
                                           options, channel_credentials,
                                           insecure, call_credentials, compression, wait_for_ready, timeout, metadata)