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

import java.io.InputStream;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.task.Task;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageContentNotFoundException;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.RetentionConfiguration;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;
import org.apache.james.vault.search.Query;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class BlobStoreDeletedMessageVault implements DeletedMessageVault {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreDeletedMessageVault.class);

    private static final String BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC = "deletedMessageVault:blobStore:";
    static final String APPEND_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "append";
    static final String LOAD_MIME_MESSAGE_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "loadMimeMessage";
    static final String SEARCH_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "search";
    static final String DELETE_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "delete";
    static final String DELETE_EXPIRED_MESSAGES_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "deleteExpiredMessages";

    private final MetricFactory metricFactory;
    private final DeletedMessageMetadataVault messageMetadataVault;
    private final BlobStore blobStore;
    private final BucketNameGenerator nameGenerator;
    private final Clock clock;
    private final RetentionConfiguration retentionConfiguration;

    @Inject
    public BlobStoreDeletedMessageVault(MetricFactory metricFactory, DeletedMessageMetadataVault messageMetadataVault,
                                 BlobStore blobStore, BucketNameGenerator nameGenerator,
                                 Clock clock,
                                 RetentionConfiguration retentionConfiguration) {
        this.metricFactory = metricFactory;
        this.messageMetadataVault = messageMetadataVault;
        this.blobStore = blobStore;
        this.nameGenerator = nameGenerator;
        this.clock = clock;
        this.retentionConfiguration = retentionConfiguration;
    }

    @Override
    public Publisher<Void> append(DeletedMessage deletedMessage, InputStream mimeMessage) {
        Preconditions.checkNotNull(deletedMessage);
        Preconditions.checkNotNull(mimeMessage);
        BucketName bucketName = nameGenerator.currentBucket();

        return metricFactory.runPublishingTimerMetric(
            APPEND_METRIC_NAME,
            appendMessage(deletedMessage, mimeMessage, bucketName));
    }

    private Mono<Void> appendMessage(DeletedMessage deletedMessage, InputStream mimeMessage, BucketName bucketName) {
        return blobStore.save(bucketName, mimeMessage)
            .map(blobId -> StorageInformation.builder()
                .bucketName(bucketName)
                .blobId(blobId))
            .map(storageInformation -> new DeletedMessageWithStorageInformation(deletedMessage, storageInformation))
            .flatMap(message -> Mono.from(messageMetadataVault.store(message)))
            .then();
    }

    @Override
    public Publisher<InputStream> loadMimeMessage(User user, MessageId messageId) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(messageId);

        return metricFactory.runPublishingTimerMetric(
            LOAD_MIME_MESSAGE_METRIC_NAME,
            Mono.from(messageMetadataVault.retrieveStorageInformation(user, messageId))
                .flatMap(storageInformation -> loadMimeMessage(storageInformation, user, messageId)));
    }

    private Mono<InputStream> loadMimeMessage(StorageInformation storageInformation, User user, MessageId messageId) {
        return Mono.fromSupplier(() -> blobStore.read(storageInformation.getBucketName(), storageInformation.getBlobId()))
            .onErrorResume(
                ObjectNotFoundException.class,
                ex -> Mono.error(new DeletedMessageContentNotFoundException(user, messageId)));
    }

    @Override
    public Publisher<DeletedMessage> search(User user, Query query) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(query);

        return metricFactory.runPublishingTimerMetric(
            SEARCH_METRIC_NAME,
            searchOn(user, query));
    }

    private Flux<DeletedMessage> searchOn(User user, Query query) {
        return Flux.from(messageMetadataVault.listRelatedBuckets())
            .concatMap(bucketName -> Flux.from(messageMetadataVault.listMessages(bucketName, user)))
            .map(DeletedMessageWithStorageInformation::getDeletedMessage)
            .filter(query.toPredicate());
    }

    @Override
    public Publisher<Void> delete(User user, MessageId messageId) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(messageId);

        return metricFactory.runPublishingTimerMetric(
            DELETE_METRIC_NAME,
            deleteMessage(user, messageId));
    }

    private Mono<Void> deleteMessage(User user, MessageId messageId) {
        return Mono.from(messageMetadataVault.retrieveStorageInformation(user, messageId))
            .flatMap(storageInformation -> Mono.from(messageMetadataVault.remove(storageInformation.getBucketName(), user, messageId))
                .thenReturn(storageInformation))
            .flatMap(storageInformation -> blobStore.delete(storageInformation.getBucketName(), storageInformation.getBlobId()))
            .subscribeOn(Schedulers.elastic());
    }

    @Override
    public Task deleteExpiredMessagesTask() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime beginningOfRetentionPeriod = now.minus(retentionConfiguration.getRetentionPeriod());

        Flux<BucketName> metricAbleDeleteOperation = metricFactory.runPublishingTimerMetric(
            DELETE_EXPIRED_MESSAGES_METRIC_NAME,
            retentionQualifiedBuckets(beginningOfRetentionPeriod)
                .flatMap(bucketName -> deleteBucketData(bucketName).then(Mono.just(bucketName))));

        return new BlobStoreVaultGarbageCollectionTask(beginningOfRetentionPeriod, metricAbleDeleteOperation);
    }

    @VisibleForTesting
    Flux<BucketName> retentionQualifiedBuckets(ZonedDateTime beginningOfRetentionPeriod) {
        return Flux.from(messageMetadataVault.listRelatedBuckets())
            .filter(bucketName -> isFullyExpired(beginningOfRetentionPeriod, bucketName));
    }

    private boolean isFullyExpired(ZonedDateTime beginningOfRetentionPeriod, BucketName bucketName) {
        Optional<ZonedDateTime> maybeEndDate = nameGenerator.bucketEndTime(bucketName);

        if (!maybeEndDate.isPresent()) {
            LOGGER.error("Pattern used for bucketName used in deletedMessageVault is invalid and end date cannot be parsed {}", bucketName);
        }
        return maybeEndDate.map(endDate -> endDate.isBefore(beginningOfRetentionPeriod))
            .orElse(false);
    }

    private Mono<Void> deleteBucketData(BucketName bucketName) {
        return blobStore.deleteBucket(bucketName)
            .then(Mono.from(messageMetadataVault.removeMetadataRelatedToBucket(bucketName)));
    }
}
