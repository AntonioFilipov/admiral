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

package com.vmware.admiral.compute.content.kubernetes;

import java.util.Map;

import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.entities.services.Service;

/**
 * KubernetesTemplate is representation of converted CompositeTemplate
 * to format which is valid and can be deployed on the top of Kubernetes.
 */
public class KubernetesTemplate {
    public Map<String, Deployment> deployments;
    public Map<String, Service> services;
}
