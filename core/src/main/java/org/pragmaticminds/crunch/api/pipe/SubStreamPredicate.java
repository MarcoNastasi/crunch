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

package org.pragmaticminds.crunch.api.pipe;

import org.pragmaticminds.crunch.api.records.MRecord;
import org.pragmaticminds.crunch.api.values.UntypedValues;

import java.io.Serializable;

/**
 * This predicate implements the filtering of incoming {@link UntypedValues} to separate those which are to
 * be processed.
 *
 * @author Erwin Wagasow
 * craeted by Erwin Wagasow on 03.08.2018
 */
@FunctionalInterface
public interface SubStreamPredicate extends Serializable {
    /**
     * Validates incoming {@link MRecord} if to be processed in that {@link SubStream}
     * @param values to be validated
     * @return true if the {@link MRecord} is to be processed
     */
    Boolean validate(MRecord values);
}
