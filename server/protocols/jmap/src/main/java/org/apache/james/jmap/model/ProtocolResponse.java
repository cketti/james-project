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

import org.apache.james.jmap.json.LegacyAwareProtocolResponseSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = LegacyAwareProtocolResponseSerializer.class)
public class ProtocolResponse {
    private static final String PLACEHOLDER_SESSION_STATE = "";

    public static ProtocolResponse create(List<InvocationResponse> methodResponses, boolean legacyRequest) {
        return new ProtocolResponse(methodResponses, PLACEHOLDER_SESSION_STATE, legacyRequest);
    }

    private final List<InvocationResponse> methodResponses;
    private final String sessionState;
    private final boolean legacyRequest;

    private ProtocolResponse(List<InvocationResponse> methodResponses, String sessionState, boolean legacyRequest) {
        this.methodResponses = Collections.unmodifiableList(methodResponses);
        this.sessionState = sessionState;
        this.legacyRequest = legacyRequest;
    }

    public List<InvocationResponse> getMethodResponses() {
        return methodResponses;
    }

    public String getSessionState() {
        return sessionState;
    }

    @JsonIgnore
    public boolean isLegacyRequest() {
        return legacyRequest;
    }
}
