/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.guice.PipelineScoped;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.errorhandler.ExpectedProcessException;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.task.GenericServerTask;
import stroom.task.TaskCallback;
import stroom.task.TaskManager;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@PipelineScoped
class ProcessorFactoryImpl implements ProcessorFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorFactoryImpl.class);
    private final TaskManager taskManager;
    private final ErrorReceiverProxy errorReceiverProxy;

    @Inject
    public ProcessorFactoryImpl(final TaskManager taskManager,
                                final ErrorReceiverProxy errorReceiverProxy) {
        this.taskManager = taskManager;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public Processor create(final List<Processor> processors) {
        if (processors == null || processors.size() == 0) {
            return null;
        }

        if (processors.size() == 1) {
            return processors.get(0);
        }

        return new MultiWayProcessor(processors, taskManager, errorReceiverProxy);
    }

    static class MultiWayProcessor implements Processor {
        private final List<Processor> processors;
        private final TaskManager taskManager;
        private final ErrorReceiver errorReceiver;

        MultiWayProcessor(final List<Processor> processors,
                          final TaskManager taskManager,
                          final ErrorReceiver errorReceiver) {
            this.processors = processors;
            this.taskManager = taskManager;
            this.errorReceiver = errorReceiver;
        }

        @Override
        public void process() {
            final CountDownLatch countDownLatch = new CountDownLatch(processors.size());
            for (final Processor processor : processors) {
                final TaskCallback<VoidResult> taskCallback = new TaskCallback<VoidResult>() {
                    @Override
                    public void onSuccess(final VoidResult result) {
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        try {
                            if (!(t instanceof ExpectedProcessException)) {
                                if (t instanceof LoggedException) {
                                    // The exception has already been logged so
                                    // ignore it.
                                    if (LOGGER.isTraceEnabled()) {
                                        LOGGER.trace(t.getMessage(), t);
                                    }
                                } else {
                                    outputError(t);
                                }
                            }
                        } finally {
                            countDownLatch.countDown();
                        }
                    }
                };

                // Try and get the parent task context.
                // TODO : @66 add links to parent task context
                final GenericServerTask task = GenericServerTask.create(null, "Process",
                        null);
                task.setRunnable(processor::process);
                taskManager.execAsync(task, taskCallback);
            }

            try {
                while (!Thread.currentThread().isInterrupted() && countDownLatch.getCount() > 0) {
                    countDownLatch.await(10, TimeUnit.SECONDS);
                }
            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);

                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Used to handle any errors that may occur during translation.
         */
        private void outputError(final Throwable t) {
            if (errorReceiver != null && !(t instanceof LoggedException)) {
                try {
                    if (t.getMessage() != null) {
                        errorReceiver.log(Severity.FATAL_ERROR, null, "MultiWayProcessor", t.getMessage(), t);
                    } else {
                        errorReceiver.log(Severity.FATAL_ERROR, null, "MultiWayProcessor", t.toString(), t);
                    }
                } catch (final RuntimeException e) {
                    // Ignore exception as we generated it.
                }

                if (errorReceiver instanceof ErrorStatistics) {
                    ((ErrorStatistics) errorReceiver).checkRecord(-1);
                }
            } else {
                LOGGER.error(MarkerFactory.getMarker("FATAL"), "Unable to output error!", t);
            }
        }
    }
}
