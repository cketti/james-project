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

package org.apache.james.task;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.task.eventsourcing.Hostname;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class MemoryTaskManager implements TaskManager {

    @FunctionalInterface
    private interface TaskExecutionDetailsUpdaterFactory {
        Consumer<TaskExecutionDetailsUpdater> apply(TaskId taskId);
    }

    private static class DetailsUpdater implements TaskManagerWorker.Listener {

        private final TaskExecutionDetailsUpdaterFactory updaterFactory;
        private final Hostname hostname;

        DetailsUpdater(TaskExecutionDetailsUpdaterFactory updaterFactory, Hostname hostname) {
            this.updaterFactory = updaterFactory;
            this.hostname = hostname;
        }

        @Override
        public void started(TaskId taskId) {
            updaterFactory.apply(taskId).accept(details -> details.started(hostname));
        }

        @Override
        public void completed(TaskId taskId, Task.Result result) {
            updaterFactory.apply(taskId)
                .accept(TaskExecutionDetails::completed);
        }

        @Override
        public void failed(TaskId taskId, Throwable t) {
            failed(taskId);
        }

        @Override
        public void failed(TaskId taskId) {
            updaterFactory.apply(taskId)
                .accept(TaskExecutionDetails::failed);
        }

        @Override
        public void cancelled(TaskId taskId) {
            updaterFactory.apply(taskId)
                .accept(TaskExecutionDetails::cancelEffectively);
        }
    }

    private static final Duration AWAIT_POLLING_DURATION = Duration.ofMillis(500);
    public static final Duration NOW = Duration.ZERO;

    private final Hostname hostname;
    private final WorkQueue workQueue;
    private final TaskManagerWorker worker;
    private final ConcurrentHashMap<TaskId, TaskExecutionDetails> idToExecutionDetails;

    @Inject
    public MemoryTaskManager(Hostname hostname) {
        this.hostname = hostname;
        this.idToExecutionDetails = new ConcurrentHashMap<>();
        this.worker = new SerialTaskManagerWorker(updater());
        workQueue = new MemoryWorkQueue(worker);
    }

    public TaskId submit(Task task) {
        TaskId taskId = TaskId.generateTaskId();
        TaskExecutionDetails executionDetails = TaskExecutionDetails.from(task, taskId, hostname);
        idToExecutionDetails.put(taskId, executionDetails);
        workQueue.submit(new TaskWithId(taskId, task));
        return taskId;
    }

    @Override
    public TaskExecutionDetails getExecutionDetails(TaskId id) {
        return Optional.ofNullable(idToExecutionDetails.get(id))
            .orElseThrow(TaskNotFoundException::new);
    }

    @Override
    public List<TaskExecutionDetails> list() {
        return ImmutableList.copyOf(idToExecutionDetails.values());
    }

    @Override
    public List<TaskExecutionDetails> list(Status status) {
        return ImmutableList.copyOf(tasksFiltered(status).values());
    }

    public Map<TaskId, TaskExecutionDetails> tasksFiltered(Status status) {
        return idToExecutionDetails.entrySet()
            .stream()
            .filter(details -> details.getValue().getStatus().equals(status))
            .collect(Guavate.entriesToImmutableMap());
    }

    @Override
    public void cancel(TaskId id) {
        Optional.ofNullable(idToExecutionDetails.get(id)).ifPresent(details -> {
                updateDetails(id).accept(taskExecutionDetails -> taskExecutionDetails.cancelRequested(hostname));
                workQueue.cancel(id);
            }
        );
    }

    @Override
    public TaskExecutionDetails await(TaskId id) {
        if (Optional.ofNullable(idToExecutionDetails.get(id)).isPresent()) {
            return Flux.interval(NOW, AWAIT_POLLING_DURATION, Schedulers.elastic())
                .map(ignored -> getExecutionDetails(id))
                .filter(details -> details.getStatus() == Status.COMPLETED
                    || details.getStatus() == Status.FAILED
                    || details.getStatus() == Status.CANCELLED)
                .take(1)
                .blockFirst();
        } else {
            return null;
        }
    }

    @PreDestroy
    public void stop() {
        try {
            workQueue.close();
        } catch (IOException ignored) {
            //avoid noise when closing the workqueue
        }
    }

    private DetailsUpdater updater() {
        return new DetailsUpdater(this::updateDetails, hostname);
    }

    private Consumer<TaskExecutionDetailsUpdater> updateDetails(TaskId taskId) {
        return updater -> {
            TaskExecutionDetails currentDetails = idToExecutionDetails.get(taskId);
            TaskExecutionDetails newDetails = updater.update(currentDetails);
            idToExecutionDetails.replace(taskId, newDetails);
        };
    }
}
