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

package org.pragmaticminds.crunch.api.state;

import com.google.common.base.Preconditions;
import org.pragmaticminds.crunch.api.exceptions.OverallTimeoutException;
import org.pragmaticminds.crunch.api.exceptions.StepTimeoutException;
import org.pragmaticminds.crunch.api.pipe.EvaluationContext;
import org.pragmaticminds.crunch.api.pipe.EvaluationFunction;
import org.pragmaticminds.crunch.api.records.MRecord;
import org.pragmaticminds.crunch.api.values.TypedValues;
import org.pragmaticminds.crunch.events.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This implementation of the {@link EvaluationFunction} represents a linear statemachine. All inner
 * {@link EvaluationFunction}s must be processed.
 *
 * @author Erwin Wagasow
 * @author kerstin
 * Created by Erwin Wagasow on 07.08.2018
 */
public class MultiStepEvaluationFunction<T extends Serializable> implements EvaluationFunction<T> {

    private static final Logger logger = LoggerFactory.getLogger(MultiStepEvaluationFunction.class);

    private final List<StateConfig<T>> stateConfigs;
    private final Long overallTimeoutMs;

    private ErrorExtractor<T> errorExtractor;
    private EvaluationCompleteExtractor<T> stateCompleteExtractor;
    private int currentStep;
    private EvaluationFunction<T> currentStateEvaluationFunction;
    private StateEvaluationContext<T> innerContext;

    private long timeoutOverallTimeStamp;
    private long timeoutStateTimeStamp;

    private boolean timersNotSet = true;

    private int numberOfEventsProcessed = 0;

    /**
     * Main constructor of this class for the Builder.
     *
     * @param stateConfigs is a list of {@link StateConfig} values, which contains a {@link EvaluationFunction} factory
     *                                  and a timeout for the chain step
     * @param overallTimeoutMs a timeout value for the complete processing duration of the processing of all inner
     *                         {@link EvaluationFunction}s
     * @param errorExtractor is a construct, that generates an error {@link Event} if a timeout should been raised
     * @param stateCompleteExtractor is a construct, that evaluates all inner {@link EvaluationFunction}s results to
     *                               generate final {@link Event}s to be send out of the {@link MultiStepEvaluationFunction}
     */
    private MultiStepEvaluationFunction(
            List<StateConfig<T>> stateConfigs,
            Long overallTimeoutMs,
            ErrorExtractor<T> errorExtractor,
            EvaluationCompleteExtractor<T> stateCompleteExtractor) {
        // Check Preconditions
        Preconditions.checkNotNull(stateConfigs);
        Preconditions.checkNotNull(errorExtractor);
        Preconditions.checkNotNull(stateCompleteExtractor);

        this.stateConfigs = new ArrayList<>(stateConfigs);
        this.overallTimeoutMs = overallTimeoutMs;
        this.errorExtractor = errorExtractor;
        this.stateCompleteExtractor = stateCompleteExtractor;

        this.currentStep = 0;
        this.currentStateEvaluationFunction = stateConfigs.get(currentStep).create();
    }

    /**
     * Creates a builder for this class
     *
     * @return a builder for this class
     */
    public static <T extends Serializable> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * sets the overall timeout (first Event timestamp + timeout in millis)
     */
    private void setOverallTimeout(long timestamp) {
        timeoutOverallTimeStamp = timestamp + overallTimeoutMs;

        if (logger.isDebugEnabled()) {
            logger.debug("Setting overall timeout for multi-stage to {}, current event timestamp is {}",
                    Instant.ofEpochMilli(timeoutOverallTimeStamp),
                    Instant.ofEpochMilli(timestamp));
        }
    }

    /**
     * sets the timeout for the state (first Event in the state's timestamp + timeout in millis)
     */
    private void setStateTimeout(long timestamp, long timeout) {
        timeoutStateTimeStamp = timestamp + timeout;

        if (logger.isDebugEnabled()) {
            logger.debug("Setting State timeout for multi-stage to {}, current event timestamp is {}",
                    Instant.ofEpochMilli(timeoutStateTimeStamp),
                    Instant.ofEpochMilli(timestamp));
        }
    }

    /**
     * evaluates the incoming {@link TypedValues} from the {@link EvaluationContext} and passes the results
     * back to the collect method of the context
     *
     * @param context contains incoming data and a collector for the outgoing data
     */
    @Override
    public void eval(EvaluationContext context) {
        try{
            // Initialize timers on first run
            MRecord record = context.get();

            // check if timeout appeared
            if (!timersNotSet) {
                checkForTimeout(record.getTimestamp());
            }

            // update or set inner context
            updateOrSetInnerContext(context);

            // execute the EvaluationFunction
            currentStateEvaluationFunction.eval(innerContext);

            // check for resulting Events
            Map<String, T> events = innerContext.getEvents();
            if (innerContext.getEvents().size() > numberOfEventsProcessed) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Received new Event, map is currently: {}", innerContext.getEvents());
                }

                // Increment the number of processed events
                numberOfEventsProcessed++;

                //set the global timeout after first successful eval
                if (timersNotSet) {
                    setOverallTimeout(record.getTimestamp());
                    timersNotSet = false;
                }

                //then go to next stage
                nextState(events, context);
            }
        } catch (Exception ex) { // on thread interrupt
            logger.info("Exception during record processing", ex);
            // Check in case Exception is e.g. a NPE for innerContext
            Map<String, T> eventsMap = (innerContext == null) ? Collections.emptyMap() : innerContext.getEvents();
            logger.debug("Calling error extractor with map {}", eventsMap);
            errorExtractor.process(eventsMap, ex, context);
            resetStatemachine();
        }
    }

    /**
     * Collects all channel identifiers, that are used for the triggering condition
     *
     * @return a {@link List} or {@link Collection} of all channel identifiers from triggering
     */
    @Override
    public Set<String> getChannelIdentifiers() {
      final Set<String> result = new HashSet<>();
      result.addAll(errorExtractor.getChannelIdentifiers());
      result.addAll(stateCompleteExtractor.getChannelIdentifiers());
      result.addAll(
          stateConfigs.stream()
                .flatMap(
                        stateConfig -> stateConfig.getFactory().getChannelIdentifiers().stream())
              .collect(Collectors.toSet())
      );
      return result;
    }

    /**
     * checks if a timeout occured and throws the corresponding Exception
     */
    private void checkForTimeout(long timestamp) throws StepTimeoutException, OverallTimeoutException {
        if (timeoutStateTimeStamp <= timestamp) {
            logger.debug("Encountered state timeout");
            throw new StepTimeoutException("Step timeout");
        }

        if (timeoutOverallTimeStamp <= timestamp) {
            logger.debug("Encountered overall timeout");
            throw new OverallTimeoutException("Overall timeout");
        }
    }

    /**
     * Updates the innerContext depending on it has already been initialized
     *
     * @param context current of the eval method
     */
    private void updateOrSetInnerContext(EvaluationContext context) {
        if (innerContext == null) {
            innerContext = new StateEvaluationContext<>(context.get(), stateConfigs.get(currentStep).getStateAlias());
        } else {
            // only set the values
            innerContext.set(context.get());
        }
    }

    /**
     * resets the inner structures in this instance, to begin processing from the start
     */
    private void resetStatemachine() {
        logger.debug("Reset state machine to initial state");
        // restart the processing
        currentStep = 0;
        currentStateEvaluationFunction = stateConfigs.get(currentStep).create();

        // No Events Processed yet
        numberOfEventsProcessed = 0;

        // reset overall timeout and state timeout
        timersNotSet = true;

        innerContext = null;
    }

    /**
     * Switches the chain to the next step, cancels if error timeout occurred or restarts if processing of the chain is
     * complete. In the last case the evaluationCompleteExtractor is called.
     * @param events so far gained from the processing of the chain steps
     * @param context current of the eval method
     */
    private void nextState(Map<String, T> events, EvaluationContext context) {
        // if timeout raised while processing
        if (currentStep == stateConfigs.size() - 1) {
            logger.debug("Reached final state, calling state complete extractor with map: {}", events);
            stateCompleteExtractor.process(events, context);
            resetStatemachine();
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Switch state from {} to {}", stateConfigs.get(currentStep).getStateAlias(), stateConfigs.get(currentStep + 1).getStateAlias());
            }
            this.setStateTimeout(context.get().getTimestamp(), stateConfigs.get(currentStep).getStateTimeout());
            currentStep++;
            currentStateEvaluationFunction = stateConfigs.get(currentStep).create();
            // Set alias for the next GenericEvent which will be written in the map
            innerContext.setAlias(stateConfigs.get(currentStep).getStateAlias());
        }
    }

    /**
     * Builder for this class
     */
    public static final class Builder<T extends Serializable> {

        private static final long DEFAULT_TIMEOUT_MS = 3_600_000;

        private List<StateConfig<T>> stateConfigs;
        private long overallTimeoutMs = DEFAULT_TIMEOUT_MS;
        private ErrorExtractor<T> errorExtractor;
        private EvaluationCompleteExtractor<T> evaluationCompleteExtractor;

        private Builder() {}

        /**
         * Adds an EvaluationFunction to the chain of {@link EvaluationFunction}s.
         *
         * @param function the {@link EvaluationFunction} that is added.
         * @param alias the alias for the results of the {@link EvaluationFunction}.
         * @return the {@link Builder}.
         */
        public Builder<T> addEvaluationFunction(EvaluationFunction<T> function, String alias) {
            return addEvaluationFunction(function, alias, DEFAULT_TIMEOUT_MS);
        }

        /**
         * Adds an EvaluationFunction to the chain of {@link EvaluationFunction}s.
         *
         * @param function the {@link EvaluationFunction} that is added.
         * @param alias the alias for the results of the {@link EvaluationFunction}.
         * @param timeoutMs the chain step timeout.
         * @return the {@link Builder}.
         */
        public Builder<T> addEvaluationFunction(EvaluationFunction<T> function, String alias, long timeoutMs) {
            CloneStateEvaluationFunctionFactory<T> factory = CloneStateEvaluationFunctionFactory.<T>builder()
                    .withPrototype(function)
                    .build();
            return addEvaluationFunctionFactory(factory, alias, timeoutMs);
        }

        /**
         * Adds an {@link EvaluationFunctionStateFactory} to the chain of {@link EvaluationFunction}s.
         *
         * @param factory that creates new instances of a {@link EvaluationFunction}, when this step is on.
         * @param alias the alias for the results of the {@link EvaluationFunction}.
         * @return the {@link Builder}
         */
        public Builder<T> addEvaluationFunctionFactory(EvaluationFunctionStateFactory<T> factory, String alias){
            return addEvaluationFunctionFactory(factory, alias, DEFAULT_TIMEOUT_MS);
        }

        /**
         * Adds an {@link EvaluationFunctionStateFactory} to the chain of {@link EvaluationFunction}s.
         *
         * @param factory that creates new instances of a {@link EvaluationFunction}, when this step is on.
         * @param alias the alias for the results of the {@link EvaluationFunction}.
         * @param timeoutMs the chain step timeout
         * @return the {@link Builder}
         */
        public Builder<T> addEvaluationFunctionFactory(EvaluationFunctionStateFactory<T> factory, String alias, long timeoutMs) {
            if (this.stateConfigs == null) {
                this.stateConfigs = new ArrayList<>();
            }
            this.stateConfigs.add(new StateConfig<>(alias, factory, timeoutMs));
            return this;
        }

        /**
         * @param overallTimeoutMs a timeout value for the complete processing duration of the processing of all inner
         *                         {@link EvaluationFunction}s
         * @return self
         */
        public Builder<T> withOverallTimeoutMs(long overallTimeoutMs) {
            this.overallTimeoutMs = overallTimeoutMs;
            return this;
        }

        /**
         * @param errorExtractor is a construct, that generates an error {@link Event} if a timeout should been raised
         * @return self
         */
        public Builder<T> withErrorExtractor(ErrorExtractor<T> errorExtractor) {
            this.errorExtractor = errorExtractor;
            return this;
        }

        /**
         * @param stateCompleteExtractor is a construct, that evaluates all inner {@link EvaluationFunction}s results to
         *                               generate final {@link Event}s to be send out of the {@link MultiStepEvaluationFunction}
         * @return self
         */
        public Builder<T> withEvaluationCompleteExtractor(EvaluationCompleteExtractor<T> stateCompleteExtractor) {
            this.evaluationCompleteExtractor = stateCompleteExtractor;
            return this;
        }

        public MultiStepEvaluationFunction<T> build() {
            return new MultiStepEvaluationFunction<>(
                    stateConfigs,
                    overallTimeoutMs,
                    errorExtractor,
                    evaluationCompleteExtractor);
        }
    }
}
