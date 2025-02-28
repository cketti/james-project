/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.modules.mailbox;

import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;

public class ListenersConfiguration {

    public static ListenersConfiguration from(HierarchicalConfiguration configuration) {
        List<HierarchicalConfiguration> listeners = configuration.configurationsAt("listener");

        return new ListenersConfiguration(listeners
            .stream()
            .map(ListenerConfiguration::from)
            .collect(Guavate.toImmutableList()));
    }
    
    private final List<ListenerConfiguration> listenersConfiguration;

    @VisibleForTesting ListenersConfiguration(List<ListenerConfiguration> listenersConfiguration) {
        this.listenersConfiguration = listenersConfiguration;
    }

    public List<ListenerConfiguration> getListenersConfiguration() {
        return listenersConfiguration;
    }
}
