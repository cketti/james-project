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
package org.apache.james.jmap.model;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.util.streams.Iterables;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;

public class ProtocolRequest {
    private static final boolean LEGACY_REQUEST = true;
    private static final boolean NON_LEGACY_REQUEST = false;

    public static ProtocolRequest deserialize(JsonNode json) {
        if (json.isArray()) {
            return deserializeLegacy(json);
        }

        Preconditions.checkState(json.isObject(), "should be a Json object");
        Preconditions.checkState(json.hasNonNull("using"), "'using' property must be present");
        Preconditions.checkState(json.get("using").isArray(), "'using' property must be an array");
        Preconditions.checkState(json.hasNonNull("methodCalls"), "'methodCalls' property must be present");
        Preconditions.checkState(json.get("methodCalls").isArray(), "'methodCalls' property must be an array");

        Set<String> using = extractCapabilities(json.get("using"));
        List<InvocationRequest> methodCalls = extractMethodCalls(json.get("methodCalls"));
        return new ProtocolRequest(using, methodCalls, NON_LEGACY_REQUEST);
    }

    @Deprecated
    private static ProtocolRequest deserializeLegacy(JsonNode json) {
        List<InvocationRequest> methodCalls = Iterables.toStream(json)
            .map(node -> InvocationRequest.deserialize(toArray(node)))
            .collect(Collectors.toList());
        return new ProtocolRequest(Collections.emptySet(), methodCalls, LEGACY_REQUEST);
    }

    private static Set<String> extractCapabilities(JsonNode usingNode) {
        return Iterables.toStream(usingNode)
            .map(node -> {
                Preconditions.checkState(node.isTextual(), "element in 'using' must be a string");
                return node.textValue();
            })
            .collect(Collectors.toSet());
    }

    private static List<InvocationRequest> extractMethodCalls(JsonNode methodCallsNode) {
        return Iterables.toStream(methodCallsNode)
            .map(node -> InvocationRequest.deserialize(toArray(node)))
            .collect(Collectors.toList());
    }

    private static JsonNode[] toArray(JsonNode node) {
        Preconditions.checkState(node.isArray(), "element in 'methodCalls' must be an array");

        JsonNode[] result = new JsonNode[node.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = node.get(i);
        }
        return result;
    }

    private final Set<String> using;
    private final List<InvocationRequest> methodCalls;
    private final boolean legacyRequest;

    private ProtocolRequest(Set<String> using, List<InvocationRequest> methodCalls, boolean legacyRequest) {
        this.using = Collections.unmodifiableSet(using);
        this.methodCalls = Collections.unmodifiableList(methodCalls);
        this.legacyRequest = legacyRequest;
    }

    public Set<String> getUsing() {
        return using;
    }

    public List<InvocationRequest> getMethodCalls() {
        return methodCalls;
    }

    @Deprecated
    public boolean isLegacyRequest() {
        return legacyRequest;
    }
}
