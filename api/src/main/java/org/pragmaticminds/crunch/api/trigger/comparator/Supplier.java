package org.pragmaticminds.crunch.api.trigger.comparator;

import org.pragmaticminds.crunch.api.records.MRecord;
import org.pragmaticminds.crunch.api.values.TypedValues;

import java.io.Serializable;
import java.util.Set;

/**
 * Compares the incoming {@link TypedValues} to internal defined criteria and returns a simplified decision base for
 * the TriggerStrategy, to make a decision if further processing is required.
 *
 * @author Erwin Wagasow
 * Created by Erwin Wagasow on 27.07.2018
 */
public interface Supplier<T> extends Serializable {
    
    /**
     * Compares the incoming values with internal criteria and returns a result of T.
     *
     * @param values incoming values to be compared to internal criteria.
     * @return a result of T.
     */
    T extract(MRecord values);
    
    /**
     * All suppliers have to be identifiable.
     *
     * @return String identifier of the {@link Supplier} implementation.
     */
    String getIdentifier();

    /**
     * Returns all channel identifiers which are necessary for the function to do its job.
     * It is not allowed to return null, an empty set can be returned (but why should??).
     *
     * @return a {@link Set} all channel identifiers that are needed by the Evaluation Function.
     */
    Set<String> getChannelIdentifiers();
}
