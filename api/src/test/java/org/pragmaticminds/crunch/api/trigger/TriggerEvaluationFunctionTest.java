package org.pragmaticminds.crunch.api.trigger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.pragmaticminds.crunch.api.pipe.SimpleEvaluationContext;
import org.pragmaticminds.crunch.api.trigger.extractor.Extractors;
import org.pragmaticminds.crunch.api.trigger.extractor.MapExtractor;
import org.pragmaticminds.crunch.api.trigger.handler.ExtractorTriggerHandler;
import org.pragmaticminds.crunch.api.trigger.handler.TriggerHandler;
import org.pragmaticminds.crunch.api.trigger.strategy.TriggerStrategy;
import org.pragmaticminds.crunch.api.values.TypedValues;
import org.pragmaticminds.crunch.api.values.dates.Value;
import org.pragmaticminds.crunch.events.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.pragmaticminds.crunch.api.trigger.comparator.Suppliers.ChannelExtractors.channel;

/**
 * @author Erwin Wagasow
 * Created by Erwin Wagasow on 27.07.2018
 */
public class TriggerEvaluationFunctionTest {
    private long timestamp = 123L;
    private Map<String, Value> valueMap = new HashMap<>();
    private Event resultEvent = new Event(
        timestamp,
        "testEventName",
        "testSource",
        valueMap
    );
    
    @Before
    public void setUp() throws Exception {
        valueMap.put("test1", Value.of(1L));
        valueMap.put("test2", Value.of(2L));
    }
    
    @Test
    public void processElementNotTriggered() {
        TriggerEvaluationFunction function = new TriggerEvaluationFunction.Builder()
            .withTriggerStrategy((TriggerStrategy) decisionBase -> false)
            .withTriggerHandler((context) -> context.collect(resultEvent))
            .build();
        
        Map<String, Value> values = new HashMap<>();
        TypedValues typedValues = new TypedValues("testSource", timestamp, values);
        List<Event> resultEventList;
        
        try {
            SimpleEvaluationContext context = new SimpleEvaluationContext(typedValues);
            resultEventList = context.getEvents();
            function.eval(context);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        Assert.assertTrue(resultEventList.isEmpty());
    }
    
    @Test
    public void processElementTriggeredNoResults() {
        TriggerHandler triggerHandler = Mockito.mock(TriggerHandler.class);
        
        TriggerEvaluationFunction function = new TriggerEvaluationFunction.Builder()
            .withTriggerStrategy((TriggerStrategy) decisionBase -> true)
            .withTriggerHandler(triggerHandler)
            .build();
        Map<String, Value> values = new HashMap<>();
        TypedValues typedValues = new TypedValues("testSource", timestamp, values);
        List<Event> resultEventList;
        try {
            SimpleEvaluationContext context = new SimpleEvaluationContext(typedValues);
            resultEventList = context.getEvents();
            function.eval(context);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        Mockito.verify(triggerHandler, Mockito.times(1)).handle(Mockito.any());
        Assert.assertTrue(resultEventList.isEmpty());
    }
    
    @Test
    public void processElementOneResult() {
        Event resultEvent = new Event(timestamp, "testEventName", "testSource");
        TriggerEvaluationFunction function = new TriggerEvaluationFunction.Builder()
            .withTriggerStrategy((TriggerStrategy) decisionBase -> true)
            .withTriggerHandler((context) -> context.collect(resultEvent))
            .build();
        Map<String, Value> values = new HashMap<>();
        TypedValues typedValues = new TypedValues("testSource", timestamp, values);
        List<Event> resultEventList;
        try {
            SimpleEvaluationContext context = new SimpleEvaluationContext(typedValues);
            resultEventList = context.getEvents();
            function.eval(context);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        Assert.assertEquals(1, resultEventList.size());
        Assert.assertTrue(resultEventList.contains(resultEvent));
    }
    
    @Test
    public void processElementManyResults() {
        TriggerEvaluationFunction function = new TriggerEvaluationFunction.Builder()
            .withTriggerStrategy((TriggerStrategy) decisionBase -> true)
            .withTriggerHandler(context -> {
                context.collect(resultEvent);
                context.collect(resultEvent);
                context.collect(resultEvent);
            })
            .build();
        Map<String, Value> values = new HashMap<>();
        TypedValues typedValues = new TypedValues("testSource", timestamp, values);
        List<Event> resultEventList;
        try {
            SimpleEvaluationContext context = new SimpleEvaluationContext(typedValues);
            resultEventList = context.getEvents();
            function.eval(context);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        Assert.assertEquals(3, resultEventList.size());
        Assert.assertTrue(resultEventList.contains(resultEvent));
    }
    
    @Test
    public void processWithResultFilter() {
        TriggerEvaluationFunction function = new TriggerEvaluationFunction.Builder()
            .withTriggerStrategy((TriggerStrategy) decisionBase -> true)
            .withTriggerHandler(context -> {
                context.collect(resultEvent);
                context.collect(resultEvent);
                context.collect(resultEvent);
            })
            .withFilter((event, values) -> values.getString("val").equals("string"))
            .build();
        Map<String, Value> values = new HashMap<>();
        values.put("val",Value.of("string"));
        TypedValues typedValues = new TypedValues("testSource", timestamp, values);
        List<Event> resultEventList;
        try {
            SimpleEvaluationContext context = new SimpleEvaluationContext(typedValues);
            resultEventList = context.getEvents();
            function.eval(context);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        Assert.assertEquals(3, resultEventList.size());
        Assert.assertTrue(resultEventList.contains(resultEvent));
    }
    
    @Test
    public void processWithTriggerHandler() {
        /*
        * Prepare testing object
        */
        
        // create channel extractors
        MapExtractor extractor1 = Extractors.channelMapExtractor(
            channel("test1")
        );
        MapExtractor extractor2 = Extractors.channelMapExtractor(
            channel("test2")
        );
        
        // create TriggerHandler
        TriggerHandler triggerHandler = new ExtractorTriggerHandler("resultEvent", extractor1, extractor2);
        
        // create TriggerEvaluationFunction
        TriggerEvaluationFunction function = new TriggerEvaluationFunction.Builder()
            .withTriggerStrategy((TriggerStrategy) decisionBase -> true)
            .withTriggerHandler(triggerHandler)
            .build();
        
        /*
        * Prepare test values
        */
        // prepare MRecord
        TypedValues typedValues = new TypedValues("testSource", timestamp, valueMap);
        
        // prepare EvaluationContext
        SimpleEvaluationContext context = new SimpleEvaluationContext(typedValues);
        
        /*
        * Execute functionality
        */
        function.eval(context);
        
        /*
         * Check results
         */
        List<Event>resultEventList = context.getEvents();
        
        // check size
        Assert.assertEquals(1, resultEventList.size());
    
        // check contents
        Event event = resultEventList.get(0);
        Assert.assertEquals(1L, (long)event.getParameter("test1").getAsLong());
        Assert.assertEquals(2L, (long)event.getParameter("test2").getAsLong());
    }
}