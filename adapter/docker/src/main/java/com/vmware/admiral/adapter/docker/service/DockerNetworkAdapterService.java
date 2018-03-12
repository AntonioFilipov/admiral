/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_ID_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_OPTIONS_PROP_NAME;

import java.net.ProtocolException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.HttpStatus;

import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class DockerNetworkAdapterService extends AbstractDockerAdapterService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_NETWORK;

    public static final String DOCKER_NETWORK_TYPE_DEFAULT =
            ContainerNetworkDescription.NETWORK_DRIVER_BRIDGE;

    private static final String DELETE_NETWORK_MISSING_ERROR = "error 404 for DELETE";

    static final List<String> DOCKER_PREDEFINED_NETWORKS = Arrays.asList("none", "host",
            "bridge", "docker_gwbridge");

    private static final List<Integer> RETRIABLE_HTTP_STATUSES = Arrays.asList(
            HttpStatus.SC_INTERNAL_SERVER_ERROR);

    private static final int NETWORK_CREATE_RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.adapter.network.create.retries", 3);

    private static class RequestContext {
        public NetworkRequest request;
        public ContainerNetworkState networkState;
        public CommandInput commandInput;
        public DockerAdapterCommandExecutor executor;

        // Only for direct operations. See DIRECT_OPERATIONS list
        public Operation operation;
    }

    @Override
    public void handlePatch(Operation op) {
        RequestContext context = new RequestContext();
        context.request = op.getBody(NetworkRequest.class);
        context.request.validate();

        NetworkOperationType operationType = context.request.getOperationType();

        logInfo("Processing network operation request %s for resource %s %s",
                operationType, context.request.resourceReference,
                context.request.getRequestTrackingLog());

        op.complete();

        processNetworkRequest(context);
    }

    /**
     * Start processing the request. First fetches the {@link ContainerNetworkState}. Will result in
     * filling the {@link RequestContext#networkState} property.
     */
    private void processNetworkRequest(RequestContext context) {
        Operation getNetworkState = Operation
                .createGet(context.request.getNetworkStateReference())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        handleExceptions(context.request, context.operation, () -> {
                            context.networkState = o
                                    .getBody(ContainerNetworkState.class);
                            processNetworkState(context);
                        });
                    }
                });
        handleExceptions(context.request, context.operation, () -> {
            logFine("Fetching NetworkState: %s %s",
                    context.request.getRequestTrackingLog(),
                    context.request.getNetworkStateReference());
            sendRequest(getNetworkState);
        });
    }

    /**
     * Process the {@link ContainerNetworkState}. Will result in filling the
     * {@link RequestContext#commandInput} and {@link RequestContext#executor} properties.
     */
    private void processNetworkState(RequestContext context) {
        if (context.networkState.originatingHostLink == null) {
            fail(context.request,
                    new IllegalArgumentException("originatingHostLink missing for network state "
                            + context.networkState.documentSelfLink));
            return;
        }

        getContainerHost(
                context.request,
                context.operation,
                UriUtils.buildUri(getHost(), context.networkState.originatingHostLink),
                (computeState, commandInput) -> {
                    context.commandInput = commandInput;
                    context.executor = getCommandExecutor();
                    handleExceptions(context.request, context.operation,
                            () -> processOperation(context));
                });
    }

    /**
     * Process the operation. This method should be called after {@link RequestContext#request},
     * {@link RequestContext#networkState}, {@link RequestContext#commandInput} and
     * {@link RequestContext#executor} have been filled.
     *
     * @see #DIRECT_OPERATIONS
     */
    private void processOperation(RequestContext context) {
        try {
            switch (context.request.getOperationType()) {
            case CREATE:
                processCreateNetwork(context, 0);
                break;

            case DELETE:
                processDeleteNetwork(context);
                break;

            case INSPECT:
                inspectAndUpdateNetwork(context);
                break;

            case LIST_NETWORKS:
                processListNetworks(context);
                break;

            case CONNECT:
                processConnectNetwork(context);
                break;

            case DISCONNECT:
                processDisconnectNetwork(context);
                break;

            default:
                fail(context.request, new IllegalArgumentException(
                        "Unexpected request type: " + context.request.getOperationType()
                                + context.request.getRequestTrackingLog()));
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }
    }

    private void processCreateNetwork(RequestContext context, int retriesCount) {
        AssertUtil.assertNotNull(context.networkState, "networkState");
        AssertUtil.assertNotEmpty(context.networkState.name, "networkState.name");

        CommandInput createCommandInput = context.commandInput.withPropertyIfNotNull(
                DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME,
                context.networkState.name);
        if (context.networkState.driver != null && !context.networkState.driver.isEmpty()) {
            createCommandInput.withProperty(
                    DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME,
                    context.networkState.driver);
        } else {
            createCommandInput.withProperty(
                    DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME,
                    DOCKER_NETWORK_TYPE_DEFAULT);
        }

        if (context.networkState.options != null && !context.networkState.options.isEmpty()) {
            createCommandInput.withProperty(
                    DOCKER_CONTAINER_NETWORK_OPTIONS_PROP_NAME,
                    context.networkState.options);
        }

        if (context.networkState.ipam != null) {
            createCommandInput.withProperty(DOCKER_CONTAINER_NETWORK_IPAM_PROP_NAME,
                    DockerAdapterUtils.ipamToMap(context.networkState.ipam));
        }

        context.executor.createNetwork(createCommandInput, (op, ex) -> {
            if (ex != null) {
                AtomicInteger retryCount = new AtomicInteger(retriesCount);
                if (RETRIABLE_HTTP_STATUSES.contains(op.getStatusCode())
                        && retryCount.getAndIncrement() < NETWORK_CREATE_RETRIES_COUNT) {
                    // retry if failure is retriable
                    logWarning("Create network %s failed with %s. Retries left %d",
                            context.networkState.name, Utils.toString(ex),
                            NETWORK_CREATE_RETRIES_COUNT - retryCount.get());
                    processCreateNetwork(context, retryCount.get());
                } else {
                    logWarning("Failure while creating network [%s]",
                            context.networkState.documentSelfLink);
                    fail(context.request, op, ex);
                }
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = op.getBody(Map.class);

                context.networkState.id = (String) body
                        .get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME);
                inspectAndUpdateNetwork(context);
                // transition to TaskStage.FINISHED is done later, after the network state gets
                // updated
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void inspectAndUpdateNetwork(RequestContext context) {

        String networkId = context.networkState.id;
        if (networkId == null) {
            fail(context.request, new IllegalStateException("network id is required "
                    + context.request.getRequestTrackingLog()));
            return;
        }

        CommandInput inspectCommandInput = new CommandInput(context.commandInput).withProperty(
                DOCKER_CONTAINER_NETWORK_ID_PROP_NAME, networkId);

        logFine("Executing inspect network: %s %s",
                context.networkState.documentSelfLink, context.request.getRequestTrackingLog());

        context.executor.inspectNetwork(
                inspectCommandInput,
                (o, ex) -> {
                    if (ex != null) {
                        logWarning("Failure while inspecting network [%s]",
                                context.networkState.documentSelfLink);
                        fail(context.request, o, ex);
                    } else {
                        handleExceptions(
                                context.request,
                                context.operation,
                                () -> {
                                    Map<String, Object> properties = o.getBody(Map.class);

                                    patchNetworkState(context.request, context.networkState,
                                            properties, context);
                                });
                    }
                });
    }

    private void patchNetworkState(NetworkRequest request, ContainerNetworkState networkState,
            Map<String, Object> properties, RequestContext context) {

        ContainerNetworkState newNetworkState = new ContainerNetworkState();
        newNetworkState.documentSelfLink = networkState.documentSelfLink;
        newNetworkState.documentExpirationTimeMicros = -1; // make sure the expiration is reset.
        newNetworkState.adapterManagementReference = networkState.adapterManagementReference;

        ContainerNetworkStateMapper.propertiesToContainerNetworkState(newNetworkState, properties);

        logFine("Patching ContainerNetworkState with properties: %s %s %s",
                newNetworkState.documentSelfLink,
                request.getRequestTrackingLog(),
                Utils.toJson(newNetworkState));
        sendRequest(Operation
                .createPatch(request.getNetworkStateReference())
                .setBodyNoCloning(newNetworkState)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failure while patching network [%s]",
                                context.networkState.documentSelfLink);
                        fail(context.request, o, ex);
                    } else {
                        patchTaskStage(request, TaskStage.FINISHED, null);
                    }
                }));
    }

    private void processDeleteNetwork(RequestContext context) {
        AssertUtil.assertNotNull(context.networkState, "networkState");
        AssertUtil.assertNotEmpty(context.networkState.id, "networkState.id");

        CommandInput deleteCommandInput = context.commandInput.withPropertyIfNotNull(
                DOCKER_CONTAINER_NETWORK_ID_PROP_NAME, context.networkState.id);

        // TODO do verification and stuff

        context.executor.removeNetwork(deleteCommandInput, (op, ex) -> {
            if (ex != null) {
                if (ex instanceof ProtocolException
                        && ex.getMessage().contains(DELETE_NETWORK_MISSING_ERROR)) {
                    logWarning("Container network %s not found", context.networkState.id);
                    patchTaskStage(context.request, TaskStage.FINISHED, null);
                } else {
                    logWarning("Failure while removing network [%s]",
                            context.networkState.documentSelfLink);
                    fail(context.request, op, ex);
                }
            } else {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            }
        });
    }

    private void processListNetworks(RequestContext context) {

        CommandInput createListNetworkCommandInput = new CommandInput(context.commandInput);

        context.executor.listNetworks(createListNetworkCommandInput, (op, ex) -> {
            if (ex != null) {
                context.operation.fail(ex);
            } else {
                if (op.hasBody()) {
                    context.operation.setBodyNoCloning(op.getBody(String.class));
                }
                context.operation.complete();
            }
        });
    }

    private void processConnectNetwork(RequestContext context) {
        // TODO implement
        throw new NotImplementedException(
                "connecting containers to networks is not implemented yet");
    }

    private void processDisconnectNetwork(RequestContext context) {
        // TODO implement
        throw new NotImplementedException(
                "disconnecting containers from networks is not implemented yet");
    }

}
