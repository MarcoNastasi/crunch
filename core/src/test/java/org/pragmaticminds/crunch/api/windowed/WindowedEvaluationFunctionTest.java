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

package org.pragmaticminds.crunch.api.windowed;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.pragmaticminds.crunch.api.pipe.ClonerUtil;
import org.pragmaticminds.crunch.api.pipe.EvaluationContext;
import org.pragmaticminds.crunch.api.pipe.SimpleEvaluationContext;
import org.pragmaticminds.crunch.api.records.MRecord;
import org.pragmaticminds.crunch.api.trigger.filter.EventFilter;
import org.pragmaticminds.crunch.api.values.TypedValues;
import org.pragmaticminds.crunch.api.values.dates.Value;
import org.pragmaticminds.crunch.api.windowed.extractor.WindowExtractor;
import org.pragmaticminds.crunch.events.GenericEvent;
import org.pragmaticminds.crunch.events.GenericEventBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.pragmaticminds.crunch.api.trigger.comparator.Suppliers.ChannelExtractors.booleanChannel;
import static org.pragmaticminds.crunch.api.windowed.Windows.bitActive;

/**
 * @author Erwin Wagasow
 * Created by Erwin Wagasow on 16.08.2018
 */
public class WindowedEvaluationFunctionTest implements Serializable {

    private WindowedEvaluationFunction<GenericEvent> function;
    private WindowedEvaluationFunction<GenericEvent> clone;
    private MRecord record1;
    private MRecord record2;
    private MRecord record3;
    private MRecord record4;
    private MRecord record5;

    @Before
    public void setUp() throws Exception {
        function = WindowedEvaluationFunction.<GenericEvent>builder()
                // set window
                .recordWindow(bitActive(booleanChannel("flag")))
                // set extractor
                .extractor(new MaxWindowExtractor())
                // set filter
                .filter(new EventFilter<GenericEvent>() {
                    @Override
                    public boolean apply(GenericEvent event, MRecord values) {
                        return true;
                    }

                    @Override
                    public Collection<String> getChannelIdentifiers() {
                        return new ArrayList<>(Collections.singletonList("test"));
                    }
                })
                .build();
        clone = ClonerUtil.clone(function);

        // create test processing data
        TypedValues.TypedValuesBuilder typedValuesBuilder = TypedValues.builder()
                .source("test")
                .timestamp(System.currentTimeMillis());

        Map<String, Value> valueMap1 = new HashMap<>();
        valueMap1.put("flag", Value.of(false));
        record1 = typedValuesBuilder.values(valueMap1).build();

        Map<String, Value> valueMap2 = new HashMap<>();
        valueMap2.put("flag", Value.of(true));
        valueMap2.put("value", Value.of(1.0));
        record2 = typedValuesBuilder.values(valueMap2).build();

        Map<String, Value> valueMap3 = new HashMap<>();
        valueMap3.put("flag", Value.of(true));
        valueMap3.put("value", Value.of(2.0));
        record3 = typedValuesBuilder.values(valueMap3).build();

        Map<String, Value> valueMap4 = new HashMap<>();
        valueMap4.put("flag", Value.of(true));
        valueMap4.put("value", Value.of(3.0));
        record4 = typedValuesBuilder.values(valueMap4).build();

        Map<String, Value> valueMap5 = new HashMap<>();
        valueMap5.put("flag", Value.of(false));
        record5 = typedValuesBuilder.values(valueMap5).build();
    }

    /**
     * Simulates a windowed processing situation
     * Messages:
     *  1. window is closed
     *  2. - 4. window is open
     *  1. window is closed -> on the message processing is expected to run
     */
    @Test
    public void eval() {
        evalTest(function);
        evalTest(clone);
    }

    private void evalTest(WindowedEvaluationFunction<GenericEvent> innerFunction) {
        SimpleEvaluationContext<GenericEvent> context = new SimpleEvaluationContext<>(record1);
        innerFunction.eval(context);
        Assert.assertEquals(0, context.getEvents().size());

        context = new SimpleEvaluationContext<>(record2);
        innerFunction.eval(context);
        Assert.assertEquals(0, context.getEvents().size());

        context = new SimpleEvaluationContext<>(record3);
        innerFunction.eval(context);
        Assert.assertEquals(0, context.getEvents().size());

        context = new SimpleEvaluationContext<>(record4);
        innerFunction.eval(context);
        Assert.assertEquals(0, context.getEvents().size());

        context = new SimpleEvaluationContext<>(record5);
        innerFunction.eval(context);
        List<GenericEvent> events = context.getEvents();
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(3.0, events.get(0).getParameter("maxValue").getAsDouble(), 0.00001);
    }

    @Test
    public void getChannelIdentifiers() {
        Collection<String> channels = function.getChannelIdentifiers();
        assertTrue(channels.contains("flag"));
        assertTrue(channels.contains("test"));

        channels = clone.getChannelIdentifiers();
        assertTrue(channels.contains("flag"));
        assertTrue(channels.contains("test"));
    }


    private class MaxWindowExtractor implements WindowExtractor<GenericEvent> {
        double maxValue = 0.0;

        @Override
        public void apply(MRecord record) {
            double value = record.getDouble("value");
            if (value > maxValue) {
                maxValue = value;
            }
        }

        @Override
        public void finish(EvaluationContext<GenericEvent> context) {
            // create a result event
            context.collect(
                    GenericEventBuilder.anEvent()
                            .withEvent("maxValue")
                            .withTimestamp(context.get().getTimestamp())
                            .withSource("test")
                            .withParameter("maxValue", maxValue)
                            .build()
            );
        }
    }
}