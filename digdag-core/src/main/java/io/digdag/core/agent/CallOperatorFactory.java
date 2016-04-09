package io.digdag.core.agent;

import java.util.List;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.ZoneId;
import java.nio.file.Path;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.digdag.spi.CommandExecutor;
import io.digdag.spi.TaskRequest;
import io.digdag.spi.TaskResult;
import io.digdag.spi.TaskReport;
import io.digdag.spi.Operator;
import io.digdag.spi.OperatorFactory;
import io.digdag.spi.TaskExecutionException;
import io.digdag.core.agent.RetryControl;
import io.digdag.core.session.SessionStateFlags;
import io.digdag.core.repository.ResourceNotFoundException;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigException;
import io.digdag.client.config.ConfigFactory;

public class CallOperatorFactory
        implements OperatorFactory
{
    private static Logger logger = LoggerFactory.getLogger(CallOperatorFactory.class);

    private final TaskCallbackApi callback;

    @Inject
    public CallOperatorFactory(TaskCallbackApi callback)
    {
        this.callback = callback;
    }

    public String getType()
    {
        return "call";
    }

    @Override
    public Operator newTaskExecutor(Path workspacePath, TaskRequest request)
    {
        return new CallOperator(callback, request);
    }

    private static class CallOperator
            implements Operator
    {
        private final TaskCallbackApi callback;
        private final TaskRequest request;
        private ConfigFactory cf;

        public CallOperator(TaskCallbackApi callback, TaskRequest request)
        {
            this.callback = callback;
            this.request = request;
            this.cf = request.getConfig().getFactory();
        }

        @Override
        public TaskResult run()
        {
            Config config = request.getConfig();

            String workflowName = config.get("_command", String.class);
            int repositoryId = config.get("repository_id", int.class);
            Config exportParams = config.getNestedOrGetEmpty("params");

            Config def;
            try {
                def = callback.getWorkflowDefinition(
                        request.getSiteId(),
                        repositoryId,
                        workflowName);
            }
            catch (ResourceNotFoundException ex) {
                throw new ConfigException(ex);
            }

            return TaskResult.defaultBuilder(request)
                .subtaskConfig(def)
                .exportParams(exportParams)
                .build();
        }
    }
}
