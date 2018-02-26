/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.ShellContainerExecutorService.ShellContainerExecutorResult;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * This test is ignored in the Bellevue-gerrit.
 * It runs in Bellevue-VIC-E2E-Tests.
 */
public class RegistryProxyIT extends BaseProvisioningOnCoreOsIT {
    private String compositeDescriptionLink;
    String testPrivateDir;
    String url;
    ShellContainerExecutorResult commandResult;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(TimeUnit.MINUTES.toSeconds(20));
    private static final int RETRY_COUNT = 3;

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        serviceClient.stop();
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {

        return compositeDescriptionLink;
    }

    @Test
    public void testRegistryProxyNoExceptions() throws Exception {
        logger.info("Add docker host.");
        setupCoreOsHost(DockerAdapterType.API, false, null);
        registryProxyHelper(false, RETRY_COUNT);
    }

    @Test
    public void testRegistryProxyWithExceptions() throws Exception {
        logger.info("Add docker host.");
        setupCoreOsHost(DockerAdapterType.API, false, null);
        registryProxyHelper(true, RETRY_COUNT);
    }

    public void registryProxyHelper(boolean withExceptionList, int retryCount) throws Exception {
        try {
            registryProxyHelper(withExceptionList);
        } catch (Throwable ex) {
            logger.error("Error using registry proxy: "
                    + Utils.toString(ex));
            if (retryCount < 0) {
                throw ex;
            } else {
                registryProxyHelper(withExceptionList, retryCount - 1);
            }
        }
    }

    public void registryProxyHelper(boolean withExceptionList) throws Exception {
        logger.info("Start squid proxy container.");
        testPrivateDir = "/etc/docker/admiral/test/" + UUID.randomUUID().toString();
        ContainerDescription containerDescSquid = new ContainerDescription();
        containerDescSquid.documentSelfLink = "squid_id";
        containerDescSquid.image = "registry.hub.docker.com/sameersbn/squid/";
        containerDescSquid.name = "squid_name";
        PortBinding portBindingSquid = new PortBinding();
        portBindingSquid.protocol = "tcp";
        portBindingSquid.containerPort = "3128";
        portBindingSquid.hostIp = "0.0.0.0";
        containerDescSquid.portBindings = new PortBinding[] { portBindingSquid };
        containerDescSquid.volumes = new String[] {
                String.format("%s/squid3:/var/log/squid3", testPrivateDir) };
        containerDescSquid = postDocument(ContainerDescriptionService.FACTORY_LINK,
                containerDescSquid);

        RequestBrokerState requestSquid = requestContainer(containerDescSquid.documentSelfLink);
        ContainerState squidState = getDocument(requestSquid.resourceLinks.iterator().next(),
                ContainerState.class);

        logger.info("Set up admiral proxy properties.");
        URI uri = URI.create(getBaseUrl() + buildServiceUri(
                ShellContainerExecutorService.SELF_LINK));
        url = UriUtils
                .appendQueryParam(uri, ShellContainerExecutorService.HOST_LINK_URI_PARAM,
                        dockerHostCompute.documentSelfLink)
                .toString();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("mkdir -p %s/properties", testPrivateDir));
        sb.append(" && ");
        sb.append(String.format("rm -f %s/properties/test.properties", testPrivateDir));
        sb.append(" && ");
        sb.append(String.format("echo 'registry.proxy=http://%s:%s' >> "
                + "%s/properties/test.properties", dockerHostCompute.address, squidState.ports
                .get(0).hostPort, testPrivateDir));

        if (withExceptionList) {
            sb.append(" && ");
            sb.append(String.format("echo 'registry.no.proxy.list=registry.hub.docker.com' >> "
                    + "%s/properties/test.properties", testPrivateDir));
        }

        executeCommand(sb.toString());

        logger.info("Start admiral container.");
        ContainerDescription containerDescAdmiral = new ContainerDescription();
        containerDescAdmiral.documentSelfLink = "admiral_id";
        containerDescAdmiral.image = "registry.hub.docker.com/vmware/admiral:dev";
        containerDescAdmiral.name = "admiral_name";
        PortBinding portBindingAdmiral = new PortBinding();
        portBindingAdmiral.protocol = "tcp";
        portBindingAdmiral.containerPort = "8282";
        portBindingAdmiral.hostIp = "0.0.0.0";
        containerDescAdmiral.portBindings = new PortBinding[] { portBindingAdmiral };
        containerDescAdmiral.volumes = new String[] {
                String.format("%s/properties:%s/properties", testPrivateDir, testPrivateDir) };
        containerDescAdmiral.env = new String[] {
                String.format("JAVA_OPTS=-Dconfiguration.properties=%s/properties/test.properties",
                        testPrivateDir) };
        containerDescAdmiral = postDocument(ContainerDescriptionService.FACTORY_LINK,
                containerDescAdmiral);

        RequestBrokerState requestAdmiral = requestContainer(containerDescAdmiral.documentSelfLink);
        ContainerState admiralState = getDocument(requestAdmiral.resourceLinks.iterator().next(),
                ContainerState.class);

        logger.info("Verify proxy logs.");
        commandResult = executeCommand(String.format("cat %s/squid3/access.log",
                testPrivateDir));

        assertTrue(commandResult.output.length() < 10);
        assertEquals(Integer.valueOf(0), commandResult.exitCode);
        logger.info("Make admiral container search request.");

        waitFor(t -> {
            try {
                HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                        String.format("http://%s:%s/templates?q=%s&documentType=true",
                                dockerHostCompute.address, admiralState.ports.get(0).hostPort,
                                UUID.randomUUID().toString()),
                        null);
                return httpResponse.statusCode == 200 && !httpResponse.responseBody.isEmpty();
            } catch (Exception e) {
                logger.warning("Unable to connect admiral: %s", e.getMessage());
                return false;
            }
        });
        for (int i = 0; i < 10; i++) {
            SimpleHttpsClient.execute(HttpMethod.GET,
                    String.format("http://%s:%s/templates?q=%s&documentType=true",
                            dockerHostCompute.address, admiralState.ports.get(0).hostPort,
                            UUID.randomUUID().toString()),
                    null);
            Thread.sleep(1000);
        }
        commandResult = executeCommand(String.format("cat %s/squid3/access.log",
                testPrivateDir));
        if (withExceptionList) {
            assertTrue(commandResult.output.length() < 10);
        } else {
            assertTrue(commandResult.output.contains("https://registry.hub.docker.com/v1/search?"));
        }
    }

    @After
    public void removeTestDir() throws Exception {
        if (!(testPrivateDir == null || testPrivateDir.isEmpty() || url == null || url.isEmpty())) {
            executeCommand(String.format("rm -r %s", testPrivateDir));
        }
    }

    private ShellContainerExecutorResult executeCommand(String command)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException,
            KeyManagementException, IOException {
        String[] commands = new String[3];
        commands[0] = "sh";
        commands[1] = "-c";
        commands[2] = String.format(" sh -c \"%s\"", command);

        Map<String, Object> commandMap = new HashMap<>();
        commandMap.put(ShellContainerExecutorService.COMMAND_KEY,
                commands);
        SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                .execute(SimpleHttpsClient.HttpMethod.POST, url,
                        Utils.toJson(commandMap));
        ShellContainerExecutorResult result = Utils.fromJson(response.responseBody,
                ShellContainerExecutorResult.class);
        logger.info("Command: %s \n Exit code : %s \n Exec output : %s",
                command, result.exitCode, result.output);
        return result;
    }
}
