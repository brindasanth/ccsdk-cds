#  Copyright © 2018-2019 AT&T Intellectual Property.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Generated by the gRPC Python protocol compiler plugin. DO NOT EDIT!
import grpc

from proto import BluePrintProcessing_pb2 as BluePrintProcessing__pb2


class BluePrintProcessingServiceStub(object):
  # missing associated documentation comment in .proto file
  pass

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
  # missing associated documentation comment in .proto file
  pass

  def process(self, request_iterator, context):
    # missing associated documentation comment in .proto file
    pass
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