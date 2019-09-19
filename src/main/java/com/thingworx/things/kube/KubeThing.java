/*
 * Copyright (c) 2018.  PTC Inc. and/or Its Subsidiary Companies. All Rights Reserved.
 * Copyright for PTC software products is with PTC Inc. and its subsidiary companies (collectively “PTC”), and their respective licensors. This software is provided under written license agreement, contains valuable trade secrets and proprietary information, and is protected by the copyright laws of the United States and other countries. It may not be copied or distributed in any form or medium, disclosed to third parties, or used in any manner not provided for in the software license agreement except with written prior approval from PTC.
 *
 */

package com.thingworx.things.kube;

import com.thingworx.data.util.InfoTableInstanceFactory;
import com.thingworx.datashape.DataShape;
import com.thingworx.entities.utils.EntityUtilities;
import com.thingworx.metadata.FieldDefinition;
import com.thingworx.metadata.annotations.*;
import com.thingworx.relationships.RelationshipTypes.ThingworxRelationshipTypes;
import com.thingworx.things.Thing;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.AppsV1beta1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.*;
import io.kubernetes.client.util.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

//import io.fabric8.kubernetes.client.Config;
//import io.kubernetes.client.util.Config;

/**
 * An implementation of the Kubernetes API
 */

@ThingworxConfigurationTableDefinitions(
        tables = {@ThingworxConfigurationTableDefinition(
                name = "ConnectionInfo",
                description = "Connection Settings",
                isMultiRow = false,
                dataShape = @ThingworxDataShapeDefinition(
                        fields = {@ThingworxFieldDefinition(
                                name = "serverName",
                                description = "Kubernetes API Server name",
                                baseType = "STRING",
                                aspects = {"defaultValue:http://127.0.0.1"}
                        ), @ThingworxFieldDefinition(
                                name = "serverPort",
                                description = "Kubernetes API Server port",
                                baseType = "NUMBER",
                                aspects = {"defaultValue:8001"}
                        ), @ThingworxFieldDefinition(
                                name = "useSSL",
                                description = "Use an SSL connection",
                                baseType = "BOOLEAN",
                                aspects = {"defaultValue:false"}
                        ), @ThingworxFieldDefinition(
                                name = "userName",
                                description = "User name",
                                baseType = "STRING",
                                aspects = {"defaultValue:kubeuser"}
                        ), @ThingworxFieldDefinition(
                                name = "password",
                                description = "Password",
                                baseType = "PASSWORD"
                        ), @ThingworxFieldDefinition(
                                name = "timeout",
                                description = "Timeout (milliseconds) to execute a request",
                                baseType = "NUMBER",
                                aspects = {"defaultValue:60000"}
                        )}
                )
        )}
)
public class KubeThing extends Thing {
    private static final String DEFAULT_URL = "http://127.0.0.1:8001";
    private static final String DEFAULT_NAME_SPACE = "default";
    private static final Integer TIME_OUT_VALUE = 180;
    private static final Logger LOGGER = LoggerFactory.getLogger(KubeThing.class);

    private int _serverPort = 8001;
    private Boolean _useSSL = false;
    private String _username = "";
    private String _password = "";
    private int _timeout = 60000;
    private String _serverName;


    public KubeThing() {
        _logger.info("KubeThing is alive");
    }

    @ThingworxServiceDefinition(
            name = "listPods",
            description = "Return a list of pods for a namespace"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "INFOTABLE"
    )

    public InfoTable listPods(@ThingworxServiceParameter(name = "namespace", description = "namespace", baseType = "STRING") String namespace,
                              @ThingworxServiceParameter(name = "dataShape", description = "datashape", baseType = "DATASHAPENAME") String dataShape)
            throws Exception {

        if (namespace != null && !namespace.isEmpty()) {
        } else {
            _logger.warn("User did not specify namespace, using default namespace");
            namespace = DEFAULT_NAME_SPACE;
        }
        InfoTable it;
        DataShape ds = (DataShape) EntityUtilities.findEntity(dataShape, ThingworxRelationshipTypes.DataShape);

        if (ds == null) {
            throw new Exception("Could not execute query because the Datashape does not exist, or a Datashape was not specified [" + dataShape + "]");
        } else {
            it = InfoTableInstanceFactory.createInfoTableFromDataShape(ds.getDataShape());

            //ApiClient client = Config.defaultClient();
            //ApiClient client = Config.fromToken("https://10.0.2.4:6443","eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNWd0d2QiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjllMWY0Yjc5LTU1ZWYtMTFlOS1hOWE5LTA4MDAyN2Q5NGUyYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.RUKpcS0wc4Mk8ow6FKoxZAJ44si4sAImAByCHfK92AuJwr8tEHSvgaP0VdrCxV8V_H024-ZsdwWk59x9U79w4WqLR8yCwB1ih1vll4o6sn9kwQSKkM29hUqQFSexZ1nIkpkeGk74_HhgCnwzzb7IEiXIeYZ5OQX0hVMeZwPJNKCSm_i0pW_vI4VyjNoqkNADNHDuKPOm3YmcxEEf_UVn_mU2aeBAOJ_nM453yyBdfFlkphn7IpM9nSREPj6kdS8TQjIsOQLHAiTWAESA2BIQcPWxaH4B-NX_EBL-a2DtRFZ2PjGSNVfLZDbaWzWmVW_Idmk9K9a5zaBfme0Ypy-SUQ" ,false);
            ApiClient client = io.kubernetes.client.util.Config.fromUrl(DEFAULT_URL, false);
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();

            //V1PodList list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
            V1PodList list = api.listNamespacedPod(namespace, null, null, null, null, null, Integer.MAX_VALUE, null, TIME_OUT_VALUE, Boolean.FALSE);

            for (V1Pod item : list.getItems()) {
                //System.out.println(item.getMetadata().getName());

                ValueCollection values = new ValueCollection();
                Iterator fieldDefinitionIterator = it.getDataShape().getFields().values().iterator();

                while (fieldDefinitionIterator.hasNext()) {
                    FieldDefinition fieldDefinition = (FieldDefinition) fieldDefinitionIterator.next();

                    Object podNameValue = item.getMetadata().getName();
                    Object podPhaseValue = item.getStatus().getPhase();
                    Object namespaceValue = item.getMetadata().getNamespace();
                    Object hostIPValue = item.getStatus().getHostIP();
                    Object podIPValue = item.getStatus().getPodIP();

                    if (podNameValue != null) {
                        values.put("podName", BaseTypes.ConvertToPrimitive(podNameValue, fieldDefinition.getBaseType()));
                        values.put("podStatus", BaseTypes.ConvertToPrimitive(podPhaseValue, fieldDefinition.getBaseType()));  //status->phase
                        values.put("namespace", BaseTypes.ConvertToPrimitive(namespaceValue, fieldDefinition.getBaseType()));
                        values.put("hostIP", BaseTypes.ConvertToPrimitive(hostIPValue, fieldDefinition.getBaseType()));
                        values.put("podIP", BaseTypes.ConvertToPrimitive(podIPValue, fieldDefinition.getBaseType()));
                    }
                }
                it.addRow(values);
            }
        }
        return it;
    }


    @ThingworxServiceDefinition(
            name = "listServices",
            description = "Return a list of services for a namespace"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "INFOTABLE"
    )
    public InfoTable listServices(@ThingworxServiceParameter(name = "namespace", description = "namespace", baseType = "STRING") String namespace,
                                  @ThingworxServiceParameter(name = "dataShape", description = "datashape", baseType = "DATASHAPENAME") String dataShape)
            throws Exception {

        if (namespace != null && !namespace.isEmpty()) {
        } else {
            _logger.warn("User did not specify namespace, using default namespace");
            namespace = DEFAULT_NAME_SPACE;
        }
        InfoTable it;
        DataShape ds = (DataShape) EntityUtilities.findEntity(dataShape, ThingworxRelationshipTypes.DataShape);

        if (ds == null) {
            throw new Exception("Could not execute query because the Datashape does not exist, or a Datashape was not specified [" + dataShape + "]");
        } else {
            it = InfoTableInstanceFactory.createInfoTableFromDataShape(ds.getDataShape());

            //ApiClient client = Config.defaultClient();
            //ApiClient client = Config.fromToken("https://10.0.2.4:6443","eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNWd0d2QiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjllMWY0Yjc5LTU1ZWYtMTFlOS1hOWE5LTA4MDAyN2Q5NGUyYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.RUKpcS0wc4Mk8ow6FKoxZAJ44si4sAImAByCHfK92AuJwr8tEHSvgaP0VdrCxV8V_H024-ZsdwWk59x9U79w4WqLR8yCwB1ih1vll4o6sn9kwQSKkM29hUqQFSexZ1nIkpkeGk74_HhgCnwzzb7IEiXIeYZ5OQX0hVMeZwPJNKCSm_i0pW_vI4VyjNoqkNADNHDuKPOm3YmcxEEf_UVn_mU2aeBAOJ_nM453yyBdfFlkphn7IpM9nSREPj6kdS8TQjIsOQLHAiTWAESA2BIQcPWxaH4B-NX_EBL-a2DtRFZ2PjGSNVfLZDbaWzWmVW_Idmk9K9a5zaBfme0Ypy-SUQ" ,false);
            ApiClient client = io.kubernetes.client.util.Config.fromUrl(DEFAULT_URL, false);
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();

            V1ServiceList list = api.listNamespacedService(namespace, null, null, null, null, null, Integer.MAX_VALUE, null, TIME_OUT_VALUE, Boolean.FALSE);

            for (V1Service item : list.getItems()) {
                //System.out.println(item.getMetadata().getName());

                ValueCollection values = new ValueCollection();
                Iterator fieldDefinitionIterator = it.getDataShape().getFields().values().iterator();

                while (fieldDefinitionIterator.hasNext()) {
                    FieldDefinition fieldDefinition = (FieldDefinition) fieldDefinitionIterator.next();

                    Object serviceNameValue = item.getMetadata().getName();

                    if (serviceNameValue != null) {
                        values.put("serviceName", BaseTypes.ConvertToPrimitive(serviceNameValue, fieldDefinition.getBaseType()));
                    }
                }
                it.addRow(values);
            }
        }
        return it;
    }

    @ThingworxServiceDefinition(
            name = "scaleDeployment",
            description = "Scale a deployment up or down. Scale to 0 to turn off"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "STRING"
    )
    public String scaleDeployment(@ThingworxServiceParameter(name = "namespace", description = "namespace", baseType = "STRING") String namespace,
                                  @ThingworxServiceParameter(name = "deploymentName", description = "name of the deployment", baseType = "STRING") String deploymentName,
                                  @ThingworxServiceParameter(name = "numberOfReplicas", description = "number of replicas to apply", baseType = "STRING") String numberOfReplicas)
            throws Exception {

        if (namespace != null && !namespace.isEmpty()) {
        } else {
            _logger.warn("User did not specify namespace, using default namespace");
            namespace = DEFAULT_NAME_SPACE;
        }

        //ApiClient client = Config.defaultClient();
        //ApiClient client = Config.fromToken("https://10.0.2.4:6443","eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNWd0d2QiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjllMWY0Yjc5LTU1ZWYtMTFlOS1hOWE5LTA4MDAyN2Q5NGUyYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.RUKpcS0wc4Mk8ow6FKoxZAJ44si4sAImAByCHfK92AuJwr8tEHSvgaP0VdrCxV8V_H024-ZsdwWk59x9U79w4WqLR8yCwB1ih1vll4o6sn9kwQSKkM29hUqQFSexZ1nIkpkeGk74_HhgCnwzzb7IEiXIeYZ5OQX0hVMeZwPJNKCSm_i0pW_vI4VyjNoqkNADNHDuKPOm3YmcxEEf_UVn_mU2aeBAOJ_nM453yyBdfFlkphn7IpM9nSREPj6kdS8TQjIsOQLHAiTWAESA2BIQcPWxaH4B-NX_EBL-a2DtRFZ2PjGSNVfLZDbaWzWmVW_Idmk9K9a5zaBfme0Ypy-SUQ" ,false);
        ApiClient client = io.kubernetes.client.util.Config.fromUrl(DEFAULT_URL, false);
        Configuration.setDefaultApiClient(client);

        ExtensionsV1beta1Api extensionV1Api = new ExtensionsV1beta1Api();
        //extensionV1Api.setApiClient(COREV1_API.getApiClient());
        extensionV1Api.setApiClient(client);
        ExtensionsV1beta1DeploymentList listNamespacedDeployment =
                extensionV1Api.listNamespacedDeployment(
                        namespace, null, null, null, null, null, null, null, null, Boolean.FALSE);

        List<ExtensionsV1beta1Deployment> extensionsV1beta1DeploymentItems =
                listNamespacedDeployment.getItems();
        Optional<ExtensionsV1beta1Deployment> findedDeployment =
                extensionsV1beta1DeploymentItems
                        .stream()
                        .filter(
                                (ExtensionsV1beta1Deployment deployment) ->
                                        deployment.getMetadata().getName().equals(deploymentName))
                        .findFirst();
        String finalNamespace = namespace;
        findedDeployment.ifPresent(
                (ExtensionsV1beta1Deployment deploy) -> {
                    try {
                        ExtensionsV1beta1DeploymentSpec newSpec = deploy.getSpec().replicas(Integer.valueOf(numberOfReplicas));
                        ExtensionsV1beta1Deployment newDeploy = deploy.spec(newSpec);
                        extensionV1Api.replaceNamespacedDeployment(
                                deploymentName, finalNamespace, newDeploy, null, null);
                    } catch (ApiException ex) {
                        LOGGER.warn("Scale the pod failed for Deployment:" + deploymentName, ex);
                    }
                });

        return "API Request was successful";
    }

    @ThingworxServiceDefinition(
            name = "deleteExtensionDeployment",
            description = "Delete an extension deployment, it will permanently remove it from Kubernetes"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "STRING"
    )
    public String deleteExtensionDeployment(@ThingworxServiceParameter(name = "namespace", description = "namespace", baseType = "STRING") String namespace,
                                            @ThingworxServiceParameter(name = "deploymentName", description = "name of the deployment", baseType = "STRING") String deploymentName)
            throws Exception {

        if (namespace != null && !namespace.isEmpty()) {
        } else {
            _logger.warn("User did not specify namespace, using default namespace");
            namespace = DEFAULT_NAME_SPACE;
        }

        //ApiClient client = Config.defaultClient();
        //ApiClient client = Config.fromToken("https://10.0.2.4:6443","eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNWd0d2QiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjllMWY0Yjc5LTU1ZWYtMTFlOS1hOWE5LTA4MDAyN2Q5NGUyYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.RUKpcS0wc4Mk8ow6FKoxZAJ44si4sAImAByCHfK92AuJwr8tEHSvgaP0VdrCxV8V_H024-ZsdwWk59x9U79w4WqLR8yCwB1ih1vll4o6sn9kwQSKkM29hUqQFSexZ1nIkpkeGk74_HhgCnwzzb7IEiXIeYZ5OQX0hVMeZwPJNKCSm_i0pW_vI4VyjNoqkNADNHDuKPOm3YmcxEEf_UVn_mU2aeBAOJ_nM453yyBdfFlkphn7IpM9nSREPj6kdS8TQjIsOQLHAiTWAESA2BIQcPWxaH4B-NX_EBL-a2DtRFZ2PjGSNVfLZDbaWzWmVW_Idmk9K9a5zaBfme0Ypy-SUQ" ,false);
        ApiClient client = io.kubernetes.client.util.Config.fromUrl(DEFAULT_URL, false);
        Configuration.setDefaultApiClient(client);

        ExtensionsV1beta1Api extensionV1Api = new ExtensionsV1beta1Api();
        //extensionV1Api.setApiClient(COREV1_API.getApiClient());
        extensionV1Api.setApiClient(client);
        ExtensionsV1beta1DeploymentList listNamespacedDeployment =
                extensionV1Api.listNamespacedDeployment(
                        namespace, null, null, null, null, null, null, null, null, Boolean.FALSE);

        List<ExtensionsV1beta1Deployment> extensionsV1beta1DeploymentItems =
                listNamespacedDeployment.getItems();
        Optional<ExtensionsV1beta1Deployment> foundDeployment =
                extensionsV1beta1DeploymentItems
                        .stream()
                        .filter(
                                (ExtensionsV1beta1Deployment deployment) ->
                                        deployment.getMetadata().getName().equals(deploymentName))
                        .findFirst();
        String finalNamespace = namespace;
        Long gracePeriod = 30L;
        foundDeployment.ifPresent(
                (ExtensionsV1beta1Deployment deploy) -> {
                    try {
                        V1DeleteOptions v1DeleteOptions = new V1DeleteOptions();
                        v1DeleteOptions.setGracePeriodSeconds(gracePeriod);
                        v1DeleteOptions.setOrphanDependents(Boolean.TRUE);
                        v1DeleteOptions.setPropagationPolicy("Background");
                        extensionV1Api.deleteNamespacedDeployment(
                                deploymentName, finalNamespace, v1DeleteOptions, null, null, 30, Boolean.TRUE, "Background");
                    } catch (ApiException ex) {
                        LOGGER.warn("Delete operation failed for :" + deploymentName, ex);
                    }
                });

        return "API Request was successful";
    }

    @ThingworxServiceDefinition(
            name = "createDeployment",
            description = "Create a deployment using the apps V1 API"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "STRING"
    )
    public String createDeployment(@ThingworxServiceParameter(name = "namespace", description = "namespace", baseType = "STRING") String namespace,
                                   @ThingworxServiceParameter(name = "filePath", description = "path to yaml deployment file ", baseType = "STRING") String filePath,
                                   @ThingworxServiceParameter(name = "deploymentName", description = "name of the deployment", baseType = "STRING") String deploymentName)
            throws Exception {

        if (namespace != null && !namespace.isEmpty()) {
        } else {
            _logger.warn("User did not specify namespace, using default namespace");
            namespace = DEFAULT_NAME_SPACE;
        }

        ApiClient client = io.kubernetes.client.util.Config.fromUrl(DEFAULT_URL, false);
        Configuration.setDefaultApiClient(client);

        AppsV1beta1Api appsV1beta1Api = new AppsV1beta1Api();
        //extensionV1Api.setApiClient(COREV1_API.getApiClient());
        appsV1beta1Api.setApiClient(client);

        //ExtensionsV1beta1Deployment v1beta1Deployment = new ExtensionsV1beta1Deployment();
        //V1ObjectMeta deploymentMetadata = new V1ObjectMeta();
        //v1beta1Deployment.setApiVersion("extensions/v1beta1");
        //v1beta1Deployment.setKind("Deployment");
        //v1beta1Deployment.setMetadata(deploymentMetadata);
        //extensionV1Api.createNamespacedDeployment(namespace, v1beta1Deployment, null, null, null);

        if (filePath != null && !filePath.isEmpty()) {
            _logger.warn("Using file path from user for deployment file");
        } else {
            filePath = "/data/deploy.yml";
        }

        try {
            //read the YAML deployment file send a create request passing it in
            Resource deployYml = new FileSystemResource(filePath);
            byte[] bdata = FileCopyUtils.copyToByteArray(deployYml.getInputStream());
            String data = new String(bdata, StandardCharsets.UTF_8);
            AppsV1beta1Deployment myDeployment = (AppsV1beta1Deployment) Yaml.load(data);

            appsV1beta1Api.createNamespacedDeployment(namespace, myDeployment, null, null, null);



        } catch (Exception ex) {
            LOGGER.warn("Request failed for Resource create or replace task:" + deploymentName, ex);
        }

        return "API Request was successful";
    }

    // todo "requestDeployment" method does not run successfully
   /* @ThingworxServiceDefinition(
            name = "requestDeployment",
            description = "Request a generic resource deployment. Can use to provision pods, services, etc. defined in YAML "
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "STRING"
    )
    public String requestDeployment(@ThingworxServiceParameter(name = "namespace", description = "namespace", baseType = "STRING") String namespace,
                                    @ThingworxServiceParameter(name = "resourceName", description = "name of the deployment", baseType = "STRING") String resourceName,
                                    @ThingworxServiceParameter(name = "kubeConfig", description = "content of deployment yaml file as string", baseType = "STRING") String kubeConfig)
            throws Exception {

        if (namespace != null && !namespace.isEmpty()) {
        } else {
            _logger.warn("User did not specify namespace, using default namespace");
            namespace = DEFAULT_NAME_SPACE;
        }

        io.fabric8.kubernetes.client.Config config = new ConfigBuilder().withMasterUrl(DEFAULT_URL).build();
        KubernetesClient kubernetesClient = new DefaultKubernetesClient(config).inNamespace(namespace);
        if (!kubernetesClient.isAdaptable(OpenShiftClient.class)) {
            _logger.warn("Builder could not create the Kubernetes client instance");
            return "Could not use the extended API call to deploy";
        }

        OpenShiftClient extendedClient = kubernetesClient.adapt(OpenShiftClient.class);

        //use ByteArrayInputStream to get the bytes of the String and convert them to InputStream.
        InputStream inputStream = new ByteArrayInputStream(kubeConfig.getBytes(Charset.forName("UTF-8")));

        try {
            // Load Yaml into Kubernetes resources
            List<HasMetadata> result = extendedClient.load(inputStream).get();
            // Apply Kubernetes Resources
            extendedClient.resourceList(result).inNamespace(namespace).createOrReplace();

        } catch (Exception ex) {
            LOGGER.warn("Request failed for Resource create or replace task:" + resourceName, ex);
        }

        return "API Request was successful";
    }*/


    @ThingworxServiceDefinition(
            name = "getPodLogs",
            description = "Get the logs for a specific pod"
    )
    @ThingworxServiceResult(
            name = "result",
            description = "Result",
            baseType = "STRING"
    )
    public String getPodLogs(@ThingworxServiceParameter(name = "namespace", description = "namespace", baseType = "STRING") String namespace,
                             @ThingworxServiceParameter(name = "podName", description = "the name of the pod", baseType = "STRING") String podName)
            throws Exception {

        String readNamespacedPodLog;

        ApiClient client = io.kubernetes.client.util.Config.fromUrl(DEFAULT_URL, false);
        Configuration.setDefaultApiClient(client);
        CoreV1Api api = new CoreV1Api();

        if (namespace != null && !namespace.isEmpty()) {
        } else {
            _logger.warn("User did not specify namespace, using default namespace");
            namespace = DEFAULT_NAME_SPACE;
        }
        readNamespacedPodLog = api.readNamespacedPodLog(podName, namespace, null, Boolean.FALSE, Integer.MAX_VALUE, null, Boolean.FALSE, Integer.MAX_VALUE, 40, Boolean.FALSE);

        return readNamespacedPodLog;
    }

    // todo Not used atm uncomment if needed
    //Use to load the kubernetes config file, can use to create the API client
/*    private ApiClient getClientFromFile() {

        //gets path to kubeconf file from system
        String conf = System.getenv("KUBERNETES_KUBECONFIG_FILE");

        try {
            ApiClient client = io.kubernetes.client.util.Config.fromConfig(conf);
            Configuration.setDefaultApiClient(client);
            client.setDebugging(Boolean.TRUE);
            Boolean clientAlive = client.isDebugging();

            if (clientAlive) {
                _logger.warn("A test client created from a kubeconf file is alive");
            }

            return client;
        } catch (IOException e) {
            _logger.warn("Could not load the configuration file");
        }
        return null;
    }*/


    protected void initializeThing() {
        this._serverName = (String) this.getConfigurationSetting("ConnectionInfo", "serverName");
        this._serverPort = ((Number) this.getConfigurationSetting("ConnectionInfo", "serverPort")).intValue();
        this._username = (String) this.getConfigurationSetting("ConnectionInfo", "userName");
        this._password = (String) this.getConfigurationSetting("ConnectionInfo", "password");
        this._timeout = ((Number) this.getConfigurationSetting("ConnectionInfo", "timeout")).intValue();
        this._useSSL = (Boolean) this.getConfigurationSetting("ConnectionInfo", "useSSL");
    }

    protected static class ConfigConstants {
        public static final String ConnectionInfo = "ConnectionInfo";
        public static final String ServerName = "serverName";
        public static final String ServerPort = "serverPort";
        public static final String UseSSL = "useSSL";
        public static final String UserName = "userName";
        public static final String Password = "password";
        public static final String Timeout = "timeout";

        protected ConfigConstants() {
        }
    }
}