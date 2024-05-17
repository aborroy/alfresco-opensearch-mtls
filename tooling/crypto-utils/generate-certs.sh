#!/bin/bash

# Custom script based in https://github.com/flavienbwk/opensearch-docker-compose/blob/main/generate-certs.sh

# Check dependencies

array=( "openssl" )
for i in "${array[@]}"
do
    command -v $i >/dev/null 2>&1 || { 
        echo >&2 "$i required"; 
        exit 1; 
    }
done

# Generate certificates for OpenSearch cluster

OPENDISTRO_DN="/C=US/ST=CLE/L=WESTLAKE/O=HYLAND"   # Change also opensearch.yml

mkdir -p certs/{ca,os-dashboards}

# Self-signed Root CA
openssl genrsa -out certs/ca/ca.key 2048
openssl req -new -x509 -sha256 -days 1095 -subj "$OPENDISTRO_DN/CN=CA" -key certs/ca/ca.key -out certs/ca/ca.pem

# Admin (used also in Alfresco Repository and Alfresco Elasticsearch Connector)
openssl genrsa -out certs/ca/admin-temp.key 2048
openssl pkcs8 -inform PEM -outform PEM -in certs/ca/admin-temp.key -topk8 -nocrypt -v1 PBE-SHA1-3DES -out certs/ca/admin.key
openssl req -new -subj "$OPENDISTRO_DN/CN=ADMIN" -key certs/ca/admin.key -out certs/ca/admin.csr
openssl x509 -req -in certs/ca/admin.csr -CA certs/ca/ca.pem -CAkey certs/ca/ca.key -CAcreateserial -sha256 -out certs/ca/admin.pem
rm certs/ca/admin-temp.key certs/ca/admin.csr

# OpenSearch Dashboards
openssl genrsa -out certs/os-dashboards/os-dashboards-temp.key 2048
openssl pkcs8 -inform PEM -outform PEM -in certs/os-dashboards/os-dashboards-temp.key -topk8 -nocrypt -v1 PBE-SHA1-3DES -out certs/os-dashboards/os-dashboards.key
openssl req -new -subj "$OPENDISTRO_DN/CN=os-dashboards" -key certs/os-dashboards/os-dashboards.key -out certs/os-dashboards/os-dashboards.csr
openssl x509 -req -in certs/os-dashboards/os-dashboards.csr -CA certs/ca/ca.pem -CAkey certs/ca/ca.key -CAcreateserial -sha256 -out certs/os-dashboards/os-dashboards.pem
rm certs/os-dashboards/os-dashboards-temp.key certs/os-dashboards/os-dashboards.csr

# OpenSearch Nodes
for NODE_NAME in "os1" "os2"
do
    mkdir "certs/${NODE_NAME}"
    openssl genrsa -out "certs/$NODE_NAME/$NODE_NAME-temp.key" 2048
    openssl pkcs8 -inform PEM -outform PEM -in "certs/$NODE_NAME/$NODE_NAME-temp.key" -topk8 -nocrypt -v1 PBE-SHA1-3DES -out "certs/$NODE_NAME/$NODE_NAME.key"
    openssl req -new -subj "$OPENDISTRO_DN/CN=$NODE_NAME" -key "certs/$NODE_NAME/$NODE_NAME.key" -out "certs/$NODE_NAME/$NODE_NAME.csr"
    openssl x509 -req -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1,DNS:$NODE_NAME") -in "certs/$NODE_NAME/$NODE_NAME.csr" -CA certs/ca/ca.pem -CAkey certs/ca/ca.key -CAcreateserial -sha256 -out "certs/$NODE_NAME/$NODE_NAME.pem"
    rm "certs/$NODE_NAME/$NODE_NAME-temp.key" "certs/$NODE_NAME/$NODE_NAME.csr"
done

chmod -R 750 ./certs