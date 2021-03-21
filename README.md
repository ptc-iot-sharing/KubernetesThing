# KubeThing

**Disclaimer**

This repository is provided "AS-IS" with **no warranty or support** given. This is not an official or supported product/use case. 

Download the extension package from

https://github.com/ptc-iot-sharing/KubernetesThing/releases/download/v1.0.263/Kube.zip


**Description**

Kubernetes is an open-source container orchestration system for automating application deployment, scaling, and management. It was originally designed by Google.

This is a ThingWorx extension that implements part of the Kubernetes API and uses the Java client library to run commands on a Kubernetes master node.

Note that this is a proof-of -concept project, meant to demonstrate that ThingWorx can integrate with Kubernetes; it would not be advisable to use the extension as is in a production environment. 
Using this project as a starting point, a ThingWorx developer interested in adding such functionality to their project, can expand it to implement more API endpoints and implement secured access/authorization.

This extension uses the official Kubernetes [Java client library](https://github.com/kubernetes-client/java) to run commands on a Kubernetes Master. 

#### Services

* listPods - Return a list of pods for a namespace

* listServices - Return a list of services for a namespace

* scaleDeployment - Scale a deployment up or down. Setting the scale to 0 will disable the deployment

* getPodLogs - Get the logs for a specific pod

* createDeployment – Create a deployment using the appsV1beta1Api.createNamespacedDeployment by passing in a AppsV1beta1Deployment YAML formatted file

  

#### Connection 

At present the extension will look for a Kubernetes master instance on localhost, without SSL. In KubeThing.java the API client is instantiated as:

```
client = Config.fromUrl("http://127.0.0.1:8001", false);
```

This type of connection requires that you run: 

```
kubectl proxy --port=8001
```

in a terminal with a user that is [configured](https://wiki.archlinux.org/index.php/Kubernetes) to use kubectl:

```
mkdir -p $HOME/.kube
sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config
```

after you have successfully started the Kubernetes master node.



#### Configuration

The configuration page of the Kubernetes Thing can be left unchanged as the fields are not usable. To change the default configuration for the target server, the extension must be rebuilt with the variables changed in the source code.
