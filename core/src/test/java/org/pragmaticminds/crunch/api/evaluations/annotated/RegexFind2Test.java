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

package org.pragmaticminds.crunch.api.evaluations.annotated;

import org.junit.Assert;
import org.junit.Test;
import org.pragmaticminds.crunch.api.AnnotatedEvalFunctionWrapper;
import org.pragmaticminds.crunch.api.EvalFunctionTestTool;
import org.pragmaticminds.crunch.api.evaluations.RegexFindTest;
import org.pragmaticminds.crunch.api.function.def.FunctionParameter;
import org.pragmaticminds.crunch.api.function.def.FunctionParameterType;
import org.pragmaticminds.crunch.api.records.DataType;
import org.pragmaticminds.crunch.api.values.dates.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Searches for the appearance of the set regex in the set channel {@link String} value
 *
 * @author Erwin Wagasow
 * Created by Erwin Wagasow on 20.10.2017
 */
public class RegexFind2Test {
    private static final Logger logger = LoggerFactory.getLogger(RegexFindTest.class);

    @Test
    public void evaluate() throws Exception {
        EvalFunctionTestTool regexFindTester = new EvalFunctionTestTool(new RegexFind2().asEvalFunction());

        Map<String, Value> literals = new HashMap<>();
        literals.put("regex", Value.of("test123"));
        List<Map<String, Value>> channels = new ArrayList<>();
        List<Long> dates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            HashMap<String, Value> channelValues = new HashMap<>();
            channelValues.put("value", Value.of("test1" + i));
            channels.add(channelValues);
            dates.add(Instant.now().toEpochMilli());
        }
        EvalFunctionTestTool.EvaluationTestToolEvents events = regexFindTester.execute(literals, channels, dates);

        // log all out and result values
        if (logger.isDebugEnabled()) {
            events.getOutputs().forEach(output -> {
                logger.debug("output: {}", output);
            });
            events.getEvents().forEach(event -> {
                logger.debug("event: {}", event);
            });
        }

        assertEquals(100, events.getOutputs().size());
        assertEquals(1, events.getEvents().size());
    }

    @Test
    public void getSignature_ofAnnotatedFunction() throws Exception {
        AnnotatedEvalFunctionWrapper<String> wrapper = new AnnotatedEvalFunctionWrapper<>(RegexFind2.class);

        FunctionParameter literalArgument = wrapper.getFunctionDef().getSignature().getArgument(0);
        FunctionParameter channelArgument = wrapper.getFunctionDef().getSignature().getArgument(1);

        // Check literal Argument
        Assert.assertEquals(DataType.STRING, literalArgument.getDataType());
        Assert.assertEquals(FunctionParameterType.LITERAL, literalArgument.getParameterType());

        // Check channel Argument
        Assert.assertEquals(DataType.STRING, channelArgument.getDataType());
        assertEquals(FunctionParameterType.CHANNEL, channelArgument.getParameterType());
    }
}