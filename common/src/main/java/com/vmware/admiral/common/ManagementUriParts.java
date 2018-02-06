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

package com.vmware.admiral.common;

import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

public interface ManagementUriParts {
    // Registry/Config/Credentials:
    String CONFIG = "/config";
    String REGISTRIES = CONFIG + "/registries";
    String REGISTRY_HOSTS = CONFIG + "/registry-spec";
    String CONFIG_PROPS = CONFIG + "/props";
    String CONFIG_CA_CREDENTIALS = CONFIG + "/ca-credentials";
    String SSL_TRUST_CERTS = CONFIG + "/trust-certs";
    String SSL_TRUST_CERTS_IMPORT = CONFIG + "/trust-certs-import";
    String USER_INITIALIZATION_SERVICE = CONFIG + "/user-init-service";
    String PROFILES = CONFIG + "/profiles";
    String PROFILE_MAPPINGS = CONFIG + "/profile-mappings";
    String COMPUTE_PROFILES = CONFIG + "/compute-profiles";
    String STORAGE_PROFILES = CONFIG + "/storage-profiles";
    String NETWORK_PROFILES = CONFIG + "/network-profiles";
    String MIGRATION = CONFIG + "/migration";

    String UTIL = "/util";
    String LONG_URI_GET = UTIL + "/long-uri-get";

    String CERT_DISTRIBUTION_ADD_REGISTRY = CONFIG + "/cert-dist-add-reg";
    String CERT_DISTRIBUTION_ADD_HOST = CONFIG + "/certs-dist-add-host";
    String ENDPOINTS = CONFIG + "/endpoints";

    String EXTENSIBILITY = "/extensibility";
    String EXTENSIBILITY_SUBSCRIPTION = EXTENSIBILITY + "-subscriptions";
    String EXTENSIBILITY_MANAGER = EXTENSIBILITY + "-manager";
    String EXTENSIBILITY_CALLBACKS = EXTENSIBILITY + "-callbacks";

    String EVENT_TOPIC_REGISTRY = CONFIG + "/event-topic";
    String EVENT_TOPIC_REGISTRY_BOOTSTRAP = CONFIG + "/event-topic-registry";

    // Resources:
    String DESCRIPTION_SUFFIX = "-descriptions";
    String CLONE_SUFFIX = "-clone";
    String EXPAND_SUFFIX = "?$expand=true";

    String RESOURCES = "/resources";
    String RESOURCE_GROUP_PLACEMENTS = RESOURCES + "/group-placements";
    String RESOURCE_NAME_PREFIXES = RESOURCES + "/name-prefixes";
    String DEPLOYMENT_POLICIES = RESOURCES + "/deployment-policies";
    String HOST_PORT_PROFILES = RESOURCES + "/host-port-profiles";
    String ELASTIC_PLACEMENT_ZONES = RESOURCES + "/elastic-placement-zones";
    String ELASTIC_PLACEMENT_ZONE_CONFIGURATION = ELASTIC_PLACEMENT_ZONES + "-config";
    String PLACEMENT_CAPACITY_UPDATE = RESOURCES + "/placement-capacity-update";
    String PLACEMENT_UPDATE_TASKS = RESOURCES + "/placement-update-tasks";
    String TAG_ASSIGNMENT = RESOURCES + "/tag-assignment";
    String COMPUTE_SEARCH = RESOURCES + "/compute-search";
    String COMPUTE_NETWORK_CIDR_ALLOCATIONS = RESOURCES + "/compute-network-cidr-allocations";

    String PROJECTS = "/projects";

    String EPZ_COMPUTE_ENUMERATION_TASKS = RESOURCES + "/epz-compute-enumeration-tasks";
    String EPZ_PERIODIC_ENUMERATION = RESOURCES + "/epz-periodic-enumeration";
    String PLACEMENT_PERIODIC_UPDATE = RESOURCES + "/placement-periodic-update";
    String COMPUTE_REMOVAL_WATCH = RESOURCES + "/compute-removal-watch";

    String CONTAINERS = RESOURCES + "/containers";
    String CONTAINER_LOGS = RESOURCES + "/container-logs";
    String CONTAINER_SHELL = RESOURCES + "/container-shell";
    String CONTAINER_DESC = RESOURCES + "/container" + DESCRIPTION_SUFFIX;
    String COMPOSITE_DESC = RESOURCES + "/composite" + DESCRIPTION_SUFFIX;
    String COMPOSITE_DESC_CLONE = RESOURCES + "/composite" + DESCRIPTION_SUFFIX + CLONE_SUFFIX;
    String COMPOSITE_DESC_CONTENT = RESOURCES + "/composite-templates";
    String COMPOSITE_COMPONENT = RESOURCES + "/composite-components";
    String COMPOSITE_CONTENT_COMPOSE = RESOURCES + "/composite-content/compose";

    String CLOSURES = RESOURCES + "/closures";
    String CLOSURES_DESC = RESOURCES + "/closure" + DESCRIPTION_SUFFIX;
    String CLOSURES_IMAGES = RESOURCES + "/closure-images";
    String CLOSURES_CONTAINER_DESC = CONTAINER_DESC + "/closure-container-desc";

    String CONTAINER_HOSTS = RESOURCES + "/hosts";
    String CONTAINER_HOST_DATA_COLLECTION = RESOURCES + "/hosts-data-collections";
    String CONTAINER_CONTROL_LOOP = RESOURCES + "/container-control-loop";
    String HOST_CONTAINER_LIST_DATA_COLLECTION = RESOURCES + "/host-container-list-data-collection";
    String HOST_NETWORK_LIST_DATA_COLLECTION = RESOURCES + "/host-network-list-data-collection";
    String HOST_VOLUME_LIST_DATA_COLLECTION = RESOURCES + "/host-volume-list-data-collection";
    String KUBERNETES_ENTITY_DATA_COLLECTION = RESOURCES
            + "/host-kubernetes-entity-data-collection";

    String EVENT_LOG = RESOURCES + "/event-logs";
    String NOTIFICATIONS = RESOURCES + "/notifications";

    String CONTAINER_NETWORKS = RESOURCES + "/container-networks";
    String CONTAINER_NETWORK_DESC = RESOURCES + "/container-network" + DESCRIPTION_SUFFIX;

    String CONTAINER_VOLUMES = RESOURCES + "/container-volumes";
    String CONTAINER_VOLUMES_DESC = RESOURCES + "/container-volume" + DESCRIPTION_SUFFIX;

    String COMPUTE_NETWORKS = RESOURCES + "/compute-networks";
    String COMPUTE_NETWORK_DESC = RESOURCES + "/compute-network" + DESCRIPTION_SUFFIX;

    String KUBERNETES = RESOURCES + "/kubernetes";
    String KUBERNETES_DESC = RESOURCES + "/kubernetes" + DESCRIPTION_SUFFIX;
    String KUBERNETES_DESC_CONTENT = RESOURCES + "/kubernetes-templates";
    String KUBERNETES_PODS = RESOURCES + "/kubernetes-pods";
    String KUBERNETES_DEPLOYMENTS = RESOURCES + "/kubernetes-deployments";
    String KUBERNETES_SERVICES = RESOURCES + "/kubernetes-services";
    String KUBERNETES_REPLICATION_CONTROLLERS = RESOURCES + "/kubernetes-replication-controllers";
    String KUBERNETES_REPLICA_SETS = RESOURCES + "/kubernetes-replica-sets";
    String KUBERNETES_POD_LOGS = RESOURCES + "/kubernetes-pod-logs";

    // Request tasks:
    String REQUEST = "/request";
    String REQUESTS = "/requests";
    String REQUEST_STATUS = "/request-status";
    String REQUEST_GRAPH = "/request-graph";
    String REQUEST_RESOURCE_OPERATIONS = REQUEST + "/resource-operations";
    String REQUEST_REMOVAL_OPERATIONS = REQUEST + "/resource-removal-operations";
    String REQUEST_ALLOCATION_TASKS = REQUEST + "/allocation-tasks";
    String REQUEST_CONTAINER_REDEPLOYMENT_TASKS = REQUEST + "/container-redeployment-tasks";
    String REQUEST_COMPUTE_ALLOCATION_TASKS = REQUEST + "/compute-allocation-tasks";
    String REQUEST_COMPUTE_NETWORK_ALLOCATION_TASKS = REQUEST
            + "/compute-network-allocation-tasks";
    String REQUEST_PROVISION_COMPUTE_NETWORK_TASKS = REQUEST
            + "/provision-compute-network-tasks";
    String REQUEST_COMPUTE_NETWORK_REMOVAL_TASKS = REQUEST + "/compute-network-removal-tasks";
    String REQUEST_CONTAINER_NETWORK_ALLOCATION_TASKS = REQUEST
            + "/container-network-allocation-tasks";
    String REQUEST_CONTAINER_NETWORK_REMOVAL_TASKS = REQUEST + "/container-network-removal-tasks";
    String REQUEST_CONTAINER_VOLUME_ALLOCATION_TASKS = REQUEST
            + "/container-volume-allocation-tasks";
    String REQUEST_CLOSURE_REMOVAL_TASKS = REQUEST + "/closure-removal-tasks";
    String REQUEST_CONTAINER_VOLUME_REMOVAL_TASKS = REQUEST + "/container-volume-removal-tasks";
    String REQUEST_CLOSURE_ALLOCATION_TASKS = REQUEST + "/closure-allocation-tasks";
    String REQUEST_CLOSURE_PROVISION_TASKS = REQUEST + "/provision-closure-tasks";
    String REQUEST_CLOSURE_RUN = REQUEST + "/closures-run";
    String REQUEST_RESERVATION_TASKS = REQUEST + "/reservation-tasks";
    String REQUEST_RESERVATION_ALLOCATION_TASKS = REQUEST + "/reservation-allocation-tasks";
    String REQUEST_COMPUTE_RESERVATION_TASKS = REQUEST + "/compute-reservation-tasks";
    String REQUEST_RESERVATION_REMOVAL_TASKS = REQUEST + "/reservation-removal-tasks";
    String REQUEST_HOST_REMOVAL_OPERATIONS = REQUEST + "/host-removal-operations";
    String REQUEST_COMPUTE_REMOVAL_OPEARTIONS = REQUEST + "/compute-removal-operations";
    String REQUEST_COMPUTE_RESOURCE_OPERATIONS = REQUEST + "/compute-resource-operations";
    String REQUEST_COMPOSITION_TASK = REQUEST + "/composition-tasks";
    String REQUEST_COMPOSITION_REMOVAL_TASK = REQUEST + "/composition-removal-tasks";
    String REQUEST_COMPOSITION_REMOVAL_KUBERNETES_TASK = REQUEST +
            "/composition-removal-kubernetes-tasks";
    String REQUEST_COMPOSITION_SUB_TASK = REQUEST + "/composition-sub-tasks";
    String REQUEST_RESOURCE_CLUSTERING_TASK = REQUEST + "/clustering-task";
    String REQUEST_PROVISION_CONTAINER_HOSTS = REQUEST + "/provision-container-hosts-tasks";
    String REQUEST_PROVISION_COMPUTE_CONTAINER_HOSTS = REQUEST
            + "/provision-compute-container-hosts-tasks";
    String REQUEST_PROVISION_CONTAINER_NETWORK_TASKS = REQUEST
            + "/provision-container-network-tasks";
    String REQUEST_PROVISION_CONTAINER_VOLUME_TASKS = REQUEST
            + "/provision-container-volume-tasks";
    String REQUEST_COMPUTE_PROVISION_TASKS = REQUEST + "/compute-provision-tasks";
    String REQUEST_PROVISION_COMPOSITE_KUBERNETES_TASKS = REQUEST
            + "/provision-composite-kubernetes-tasks";
    String REQUEST_PROVISION_PLACEMENT_TASKS = REQUEST + "/placement-tasks";
    String REQUEST_PROVISION_COMPUTE_PLACEMENT_TASKS = REQUEST + "/compute-placement-tasks";
    String REQUEST_PROVISION_NAME_PREFIXES_TASKS = REQUEST + "/resource-prefix-tasks";
    String REQUEST_PROVISION_SERVICE_LINKS_TASKS = REQUEST + "/service-link-processing-tasks";
    String REQUEST_PROVISION_EXPOSE_SERVICE_TASKS = REQUEST + "/expose-service-processing-tasks";
    String REQUEST_CALLBACK_HANDLER_TASKS = REQUEST + "/callback-handler/";
    String REQUEST_CONTAINER_PORTS_ALLOCATION_TASKS = REQUEST + "/container-ports-allocation-tasks";
    String REQUEST_ENDPOINT_HEALTHCHECK_TASKS = REQUEST + "/endpoint-healthcheck-tasks";
    String ENDPOINT_PERIODIC_HEALTHCHECK = REQUEST + "/endpoint-periodic-healthcheck";

    String CONFIGURE_HOST = REQUEST + "/configure-host/";

    String DELETE_SERVICE_DOCUMENTS = "/delete-tasks";

    String COUNTER_SUB_TASKS = "/counter-subtasks";
    // Continuous delivery:
    String CONTINUOUS_DELIVERY = "/continous-delivery";

    String SELF_PROVISIONING = CONTINUOUS_DELIVERY + "/self-provisioning";
    // Image operations:
    String IMAGES = "/images";
    String IMAGE_TAGS = IMAGES + "/tags";
    String TEMPLATES = "/templates";
    String LOGS = "/logs";

    String POPULAR_IMAGES = "/popular-images";
    // Adapters:
    String ADAPTERS = "/adapters";
    String ADAPTER_DOCKER = ADAPTERS + "/docker-service";
    String ADAPTER_DOCKER_HOST = ADAPTERS + "/host-docker-service";
    String ADAPTER_DOCKER_IMAGE_HOST = ADAPTERS + "/host-docker-image-service";
    String ADAPTER_DOCKER_OPERATIONS = ADAPTER_DOCKER + "/operations";
    String ADAPTER_REGISTRY = ADAPTERS + "/registry-service";
    String ADAPTER_DOCKER_VOLUME = ADAPTERS + "/volume-docker-service";
    String ADAPTER_DOCKER_NETWORK = ADAPTERS + "/network-docker-service";

    String ADAPTER_KUBERNETES = ADAPTERS + "/kubernetes-service";
    String ADAPTER_KUBERNETES_HOST = ADAPTERS + "/host-kubernetes-service";
    String ADAPTER_KUBERNETES_NETWORK = ADAPTERS + "/network-kubernetes-service";
    String ADAPTER_KUBERNETES_APPLICATION = ADAPTERS + "/application-kubernetes-service";

    String ADAPTER_ETCD_KV = "/v2/keys";
    String ADAPTER_ETCD_MEMBERS = "/v2/members";

    String KV_STORE = "/kv-store";
    // UI Service:
    String UI_SERVICE = System.getProperty("dcp.management.ui.path", "/");
    String UI_NG_SERVICE = UI_SERVICE + "ng";

    String CONTAINER_ICONS_RESOURCE_PATH = UI_SERVICE + "/container-icons";
    String CONTAINER_IDENTICONS_RESOURCE_PATH = UI_SERVICE + "/container-identicons";
    String CONTAINER_IMAGE_ICONS = UI_SERVICE + "/container-image-icons";

    String REVERSE_PROXY = "/rp";

    String USER_SESSION_SERVICE = "/user-session";

    String REQUEST_PARAM_VALIDATE_OPERATION_NAME = "validate";
    String REQUEST_PARAM_ENUMERATE_OPERATION_NAME = "enumerate";

    String REQUEST_PARAM_TARGET_RESOURCE_POOL_LINK = "targetRpLink";

    String EXEC = "/exec";
    String AUTH_CREDENTIALS_CA_LINK = UriUtils.buildUriPath(
            AuthCredentialsService.FACTORY_LINK, "default-ca-cert");
    String AUTH_CREDENTIALS_CLIENT_LINK = UriUtils.buildUriPath(
            AuthCredentialsService.FACTORY_LINK, "default-client-cert");
}
