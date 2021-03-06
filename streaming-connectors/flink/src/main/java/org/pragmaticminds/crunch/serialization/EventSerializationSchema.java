/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.pragmaticminds.crunch.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.pragmaticminds.crunch.events.GenericEvent;
import org.pragmaticminds.crunch.events.UntypedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a schema that serializes an GenericEvent datatype
 * Created by timbo on 24.11.17
 */
public class EventSerializationSchema implements SerializationSchema<GenericEvent> {

    private static final Logger logger = LoggerFactory.getLogger(EventSerializationSchema.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public EventSerializationSchema() {
        //sonar wants it that way
    }

    /**
     * serializes an GenericEvent to a byte array
     *
     * @param element the GenericEvent that shall be serialized
     * @return the equivalent serialized byte array
     */
    static byte[] serializeEvent(GenericEvent element) {
        try {
            UntypedEvent untypedEvent = UntypedEvent.fromEvent(element);
            return objectMapper.writeValueAsBytes(untypedEvent);
        } catch (JsonProcessingException e) {
            logger.error("could not deserialize GenericEvent", e);
            return new byte[0];
        }
    }

    /**
     * Serializes the incoming element to a specified type.
     *
     * @param element The incoming element to be serialized
     * @return The serialized element.
     */
    @Override
    public byte[] serialize(GenericEvent element) {
        return EventSerializationSchema.serializeEvent(element);
    }
}
