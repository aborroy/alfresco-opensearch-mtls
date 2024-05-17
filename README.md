# Alfresco Enterprise Docker Compose deployment using mTLS communication with OpenSearch

This project provides a *ready-to-use* Docker Compose to use **mTLS** communication between Alfresco Repository and Alfresco Search Enterprise (OpenSearch). Note that deploying the product in *production* environments would require additional configuration.

Docker Images from [quay.io](https://quay.io/organization/alfresco) are used, since this product is only available for Alfresco Enterprise customers. In addition, [Alfresco Nexus](https://nexus.alfresco.com) credentials may be required. If you are Enterprise Customer or Partner but you are still experimenting problems to download Docker Images or download artifacts from Nexus, contact [Alfresco Hyland Support](https://community.hyland.com) in order to get required credentials and permissions.

This project provides following folders:

* [docker](docker) folder contains Docker Compose template to deploy ACS Enterprise 23.2 using Search Enterprise (OpenSearch 2.14.0) with mTLS communication
* [tooling](tooling) folder contains an Alfresco Enterprise Repository patch and cryptographic tools to create required certificates and keystores

## Running Docker Compose

Run [docker](docker) compose

```sh
cd docker
docker compose up
```

Once the stack is up & ready (it may take 1-2 minutes), following endpoints are available:

* Alfresco Repository: http://localhost:8080/alfresco
* Alfresco Share: http://localhost:8080/share
* Alfresco ADW: http://localhost:8080/workspace
* OpenSearch Dashboards: https://localhost:5601

>> Use `admin`/`admin` credentials for every endpoint

## Create custom certificates and keystores

Default certificates and keystores are provided in Docker Compose:

* [docker/search/certs](docker/search/certs) includes the *self-signed* CA (`ca`) and certificates for OpenSearch Dashboards (`os-dashboards`) and OpenSearch Nodes (`os1` and `os2`)
* [docker/search/keystore](docker/search/keystore) includes a keystore and a trustore for Alfresco Elasticsearch Connector
* [docker/repo/keystore](docker/repo/keystore) includes a keystore and a trustore for Alfresco Repository

Sample scripts to create custom certificates and keystores are available in [tooling/crypto-utils](tooling/crypto-utils) folder:

```sh
cd tooling/crypto-utils
```

Creating required certificates for OpenSearch is provided by `generate-certs.sh` script. Once the certificates are ready, use `create-keystores.sh` to build keystore and truststore.

```sh
./generate-certs.sh

./create-keystores.sh

tree
.
├── alfresco.keystore
├── alfresco.truststore
├── certs
│   ├── ca
│   │   ├── admin.key
│   │   ├── admin.pem
│   │   ├── ca.key
│   │   ├── ca.pem
│   │   └── ca.srl
│   ├── os-dashboards
│   │   ├── os-dashboards.key
│   │   └── os-dashboards.pem
│   ├── os1
│   │   ├── os1.key
│   │   └── os1.pem
│   └── os2
│       ├── os2.key
│       └── os2.pem
├── create-keystores.sh
└── generate-certs.sh
```

>> It may be required to adapt the content of these scripts according to your specific requirements


## Alfresco Enterprise Repository path

Since Alfresco Enterprise 23.2.1 doesn't support mTLS communication with Search Enterprise by default, [tooling/mtls-search-enterprise](tooling/mtls-search-enterprise) project provides a Search Subsystem extension to add this feature. This option will be available in Alfresco Enterprise eventually, once the [JIRA issue](https://hyland.atlassian.net/browse/ACS-7965) has been solved.

The artifact is already available in [docker/repo](docker/repo) folder as `mtls-search-enterprise-0.8.0` and it's applied to Docker Compose deployment.