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

package org.apache.james.vault.blob;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.james.blob.api.BucketName;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class BlobStoreVaultGarbageCollectionTask implements Task {

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final ZonedDateTime beginningOfRetentionPeriod;
        private final Collection<BucketName> deletedBuckets;

        AdditionalInformation(ZonedDateTime beginningOfRetentionPeriod, Collection<BucketName> deletedBuckets) {
            this.beginningOfRetentionPeriod = beginningOfRetentionPeriod;
            this.deletedBuckets = deletedBuckets;
        }

        public ZonedDateTime getBeginningOfRetentionPeriod() {
            return beginningOfRetentionPeriod;
        }

        public List<String> getDeletedBuckets() {
            return deletedBuckets.stream()
                .map(BucketName::asString)
                .collect(Guavate.toImmutableList());
        }
    }

    private static final TaskType TYPE = TaskType.of("deletedMessages/blobStoreBasedGarbageCollection");

    private final Flux<BucketName> retentionOperation;
    private final ZonedDateTime beginningOfRetentionPeriod;
    private final Collection<BucketName> deletedBuckets;

    BlobStoreVaultGarbageCollectionTask(ZonedDateTime beginningOfRetentionPeriod, Flux<BucketName> retentionOperation) {
        this.retentionOperation = retentionOperation;
        this.beginningOfRetentionPeriod = beginningOfRetentionPeriod;
        this.deletedBuckets = new ConcurrentLinkedQueue<>();
    }

    @Override
    public Result run() {
        retentionOperation
            .doOnNext(deletedBuckets::add)
            .subscribeOn(Schedulers.elastic())
            .then()
            .block();

        return Result.COMPLETED;
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(beginningOfRetentionPeriod, deletedBuckets));
    }
}
