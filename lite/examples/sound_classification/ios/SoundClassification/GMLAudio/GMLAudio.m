// Copyright 2022 The TensorFlow Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#import "GMLAudio.h"
#import "GMLAudioError.h"
#import "GMLUtils.h"

@implementation GMLAudio {
  GMLRingBuffer *_ringBuffer;
}

- (instancetype)initWithAudioFormat:(GMLAudioFormat *)format sampleCount:(NSUInteger)sampleCount {
  self = [self init];
  if (self) {
    _audioFormat = format;
    _ringBuffer = [[GMLRingBuffer alloc] initWithBufferSize:sampleCount * format.channelCount];
  }
  return self;
}

- (BOOL)loadWithBuffer:(GMLFloatBuffer *)floatBuffer
                offset:(NSInteger)offset
                  size:(NSInteger)size
                 error:(NSError **)error {
  return [_ringBuffer loadWithBuffer:floatBuffer offset:offset size:floatBuffer.size error:error];
}

- (BOOL)loadAudioRecordBuffer:(GMLFloatBuffer *)floatBuffer withError:(NSError **)error {
  // Sample rate and channel count need not be checked here as they will be checked and reported
  // when TFLAudioRecord which created this record buffer starts emitting wrong sized buffers.

  // Checking buffer size makes sure that channel count and buffer size match.
  if (_ringBuffer.buffer.size != floatBuffer.size) {
    [GMLUtils createCustomError:error
                       withCode:GMLAudioErrorCodeInvalidArgumentError
                    description:@"Size of TFLAudioRecord buffer does not match GMLAudio's buffer "
                                @"size. Please make sure that the TFLAudioRecord object which "
                                @"created floatBuffer is initialized with the same format "
                                @"(channels, sampleRate) and buffer size as GMLAudio."];
    return NO;
  }

  return [self loadWithBuffer:floatBuffer offset:0 size:floatBuffer.size error:error];
}

- (GMLFloatBuffer *)getBuffer {
  return [_ringBuffer.buffer copy];
}

@end