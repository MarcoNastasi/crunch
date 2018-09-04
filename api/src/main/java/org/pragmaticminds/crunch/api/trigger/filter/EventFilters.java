package org.pragmaticminds.crunch.api.trigger.filter;

import org.pragmaticminds.crunch.api.records.MRecord;
import org.pragmaticminds.crunch.api.trigger.comparator.Supplier;
import org.pragmaticminds.crunch.events.Event;

import java.io.Serializable;

/**
 * This is a collection of filters for usual use cases
 *
 * @author Erwin Wagasow
 * Created by Erwin Wagasow on 14.08.2018
 */
public class EventFilters {
    private EventFilters(){ /* hide constructor */ }
    
    /**
     * This EventFilter checks if a particular value in the {@link MRecord} has changed since the last processing
     *
     * @param supplier extracts the value of interest out of the {@link MRecord}
     * @return true if the value of interest has been changed since the last processing
     */
    public static <T extends Serializable> EventFilter onValueChanged(Supplier<T> supplier){
        return new EventFilter() {
            private T lastValue;
    
            /**
             * Applies the filtering checking on the current {@link Event} and {@link MRecord}
             *
             * @param event the extracted from the processing
             * @param values the processed values
             * @return true if this {@link Event} is to be filtered out, otherwise false
             */
            @Override
            public boolean apply(Event event, MRecord values) {
                boolean keep = false; // filter by default
                T value = supplier.extract(values);
                if(lastValue != null){
                    keep = !value.equals(lastValue);
                }
                lastValue = value;
                return keep;
            }
        };
    }
}