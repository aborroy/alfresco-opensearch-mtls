# Alfresco Enterprise Docker Compose deployment using mTLS communication with OpenSearch

This folders includes a regular Docker Compose template for ACS 23.2 deployment. 

Following, some details for Repository and OpenSearch configuration are highlighted.

```sh
├── repo
│   ├── compose.yaml                        Alfresco Repository container
│   ├── keystore/                           Keystore and Truststore to connect with OpenSearch
│   └── mtls-search-enterprise-0.8.0.jar    Alfresco Repository addon that extends Search Subsystem to patch ACS-7965
└── search
│   ├── certs/                              Certificates required to configure OpenSearch cluster to support mTLS
│   ├── keystore/                           Keystore and Truststore to connect ESC with OpenSearch using mTLS
│   ├── compose.yaml                        OpenSearch, OpenSearch Dashboards, ESC Live Indexing and ESC Reindexing containers
│   ├── opensearch-dashboards.yml           Customization for OpenSearch Dashboards
│   ├── opensearch.yml                      Customization for OpenSearch
│   └── reindex.prefixes-file.json          Helping file for ESC Reindexing built with https://github.com/AlfrescoLabs/model-ns-prefix-mapping
```

[repo/compose.yaml](repo/compose.yaml) - Repository configuration

```yaml
services:
  alfresco:        
    image: quay.io/alfresco/alfresco-content-repository:${ALFRESCO_TAG}
    environment:
      JAVA_OPTS: >-
        -Delasticsearch.createIndexIfNotExists=true
        -Dindex.subsystem.name=elasticsearch
        -Delasticsearch.host=os1
        -Delasticsearch.indexName=${OPENSEARCH_INDEX_NAME}
        -Delasticsearch.secureComms=mtls
        -Delasticsearch.ssl.host.name.verification=false
        -Dencryption.ssl.truststore.location=/usr/local/tomcat/alfresco.truststore
        -Dssl-truststore.password=truststore
        -Dencryption.ssl.truststore.type=PKCS12
        -Dencryption.ssl.keystore.location=/usr/local/tomcat/alfresco.keystore
        -Dssl-keystore.password=keystore
        -Dencryption.ssl.keystore.type=PKCS12
    volumes:
      - ./keystore/alfresco.keystore:/usr/local/tomcat/alfresco.keystore:ro
      - ./keystore/alfresco.truststore:/usr/local/tomcat/alfresco.truststore:ro
      - ./mtls-search-enterprise-0.8.0.jar:/usr/local/tomcat/webapps/alfresco/WEB-INF/lib/mtls-search-enterprise-0.8.0.jar
```

[search/compose.yaml](search/compose.yaml) - OpenSearch cluster configuration

```yaml
  os1:
    image: opensearchproject/opensearch:${OPENSEARCH_TAG}
    environment:
      plugins.security.ssl.transport.pemkey_filepath: certificates/os1/os1.key
      plugins.security.ssl.transport.pemcert_filepath: certificates/os1/os1.pem
      plugins.security.ssl.http.pemkey_filepath: certificates/os1/os1.key
      plugins.security.ssl.http.pemcert_filepath: certificates/os1/os1.pem
      plugins.security.ssl.http.enabled: true
      plugins.security.ssl.http.pemtrustedcas_filepath: certificates/ca/ca.pem
      plugins.security.ssl.transport.enabled: true
      plugins.security.ssl.transport.pemtrustedcas_filepath: certificates/ca/ca.pem
      plugins.security.ssl.transport.enforce_hostname_verification: false
      plugins.security.allow_unsafe_democertificates: true
    command: >
      sh -c 'echo "sleep 30 && chmod +x /usr/share/opensearch/plugins/opensearch-security/tools/securityadmin.sh &&
             /usr/share/opensearch/plugins/opensearch-security/tools/securityadmin.sh \
             -cd /usr/share/opensearch/config/opensearch-security -icl -nhnv \
             -cacert /usr/share/opensearch/config/certificates/ca/ca.pem \
             -cert /usr/share/opensearch/config/certificates/ca/admin.pem \
             -key /usr/share/opensearch/config/certificates/ca/admin.key -h localhost &" >> /usr/share/opensearch/apply-securityadmin.sh &&
             chmod +x /usr/share/opensearch/apply-securityadmin.sh &&
             /usr/share/opensearch/apply-securityadmin.sh &
             /usr/share/opensearch/opensearch-docker-entrypoint.sh'
    volumes:
      - ./opensearch.yml:/usr/share/opensearch/config/opensearch.yml
      - ./certs:/usr/share/opensearch/config/certificates:ro

  os2:
    image: opensearchproject/opensearch:${OPENSEARCH_TAG}
    environment:
      plugins.security.ssl.transport.pemkey_filepath: certificates/os2/os2.key
      plugins.security.ssl.transport.pemcert_filepath: certificates/os2/os2.pem
      plugins.security.ssl.http.pemkey_filepath: certificates/os2/os2.key
      plugins.security.ssl.http.pemcert_filepath: certificates/os2/os2.pem
      plugins.security.ssl.http.enabled: true
      plugins.security.ssl.http.pemtrustedcas_filepath: certificates/ca/ca.pem
      plugins.security.ssl.transport.enabled: true
      plugins.security.ssl.transport.pemtrustedcas_filepath: certificates/ca/ca.pem
      plugins.security.ssl.transport.enforce_hostname_verification: false
      plugins.security.allow_unsafe_democertificates: true
    command: >
      sh -c 'echo "sleep 30 && chmod +x /usr/share/opensearch/plugins/opensearch-security/tools/securityadmin.sh &&
             /usr/share/opensearch/plugins/opensearch-security/tools/securityadmin.sh \
             -cd /usr/share/opensearch/config/opensearch-security -icl -nhnv \
             -cacert /usr/share/opensearch/config/certificates/ca/ca.pem \
             -cert /usr/share/opensearch/config/certificates/ca/admin.pem \
             -key /usr/share/opensearch/config/certificates/ca/admin.key -h localhost &" >> /usr/share/opensearch/apply-securityadmin.sh &&
             chmod +x /usr/share/opensearch/apply-securityadmin.sh &&
             /usr/share/opensearch/apply-securityadmin.sh &
             /usr/share/opensearch/opensearch-docker-entrypoint.sh'
    volumes:
      - ./opensearch.yml:/usr/share/opensearch/config/opensearch.yml
      - ./certs:/usr/share/opensearch/config/certificates:ro

```

[search/opensearch.yml](search/opensearch.yml) - OpenSearch customization

```yaml
plugins.security.authcz.admin_dn:
  - 'CN=ADMIN,O=HYLAND,L=WESTLAKE,ST=CLE,C=US'
plugins.security.nodes_dn:
  - 'CN=os1,O=HYLAND,L=WESTLAKE,ST=CLE,C=US'
  - 'CN=os2,O=HYLAND,L=WESTLAKE,ST=CLE,C=US'
```

[search/compose.yaml](search/compose.yaml) - OpenSearch Dashboards configuration

```yaml      
  opensearch-dashboards:
    image: opensearchproject/opensearch-dashboards:${OPENSEARCH_DASHBOARDS_TAG}
    environment:
      OPENSEARCH_HOSTS: '["https://os1:9200","https://os2:9200"]'
    volumes:
      - ./certs:/usr/share/opensearch-dashboards/config/certificates:ro
      - ./opensearch-dashboards.yml:/usr/share/opensearch-dashboards/config/opensearch_dashboards.yml
```

[search/opensearch_dashboards.yml](search/opensearch_dashboards.yml) - OpenSearch Dashboards customization

```yaml
server.name: os_dashboards
server.host: "0.0.0.0"

opensearch.username: "admin"
opensearch.password: "admin"

# Encrypt traffic between the browser and OpenSearch-Dashboards
server.ssl.enabled: true
server.ssl.certificate: "/usr/share/opensearch-dashboards/config/certificates/os-dashboards/os-dashboards.pem"
server.ssl.key: "/usr/share/opensearch-dashboards/config/certificates/os-dashboards/os-dashboards.key"

# Encrypt traffic between OpenSearch-Dashboards and Opensearch
opensearch.ssl.certificateAuthorities: ["/usr/share/opensearch-dashboards/config/certificates/ca/ca.pem"]
opensearch.ssl.verificationMode: full
```

[search/compose.yaml](search/compose.yaml) - ESC Indexing and Reindexing configuration

```yaml
  live-indexing:
    image: quay.io/alfresco/alfresco-elasticsearch-live-indexing:${LIVE_INDEXING_TAG}
    environment:
      SPRING_ELASTICSEARCH_REST_URIS: https://os1:9200,https://os2:9200
      JAVA_OPTS: '
          -Djavax.net.ssl.trustStore=/tmp/esc.truststore
          -Djavax.net.ssl.trustStoreType=PKCS12
          -Djavax.net.ssl.trustStorePassword=truststore
          -Djavax.net.ssl.keyStore=/tmp/esc.keystore
          -Djavax.net.ssl.keyStoreType=PKCS12
          -Djavax.net.ssl.keyStorePassword=keystore
      '      
    volumes:
      - ./keystore/esc.keystore:/tmp/esc.keystore:ro
      - ./keystore/esc.truststore:/tmp/esc.truststore:ro

  reindexing:
    image: quay.io/alfresco/alfresco-elasticsearch-reindexing:${LIVE_INDEXING_TAG}
    environment:
      SPRING_ELASTICSEARCH_REST_URIS: https://os1:9200,https://os2:9200
      JAVA_OPTS: '
          -Djavax.net.ssl.trustStore=/tmp/esc.truststore
          -Djavax.net.ssl.trustStoreType=PKCS12
          -Djavax.net.ssl.trustStorePassword=truststore
          -Djavax.net.ssl.keyStore=/tmp/esc.keystore
          -Djavax.net.ssl.keyStoreType=PKCS12
          -Djavax.net.ssl.keyStorePassword=keystore
      '      
    volumes:
      - ./keystore/esc.keystore:/tmp/esc.keystore:ro
      - ./keystore/esc.truststore:/tmp/esc.truststore:ro      
```