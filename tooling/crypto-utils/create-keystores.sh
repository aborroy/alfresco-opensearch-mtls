#!/bin/bash

# Check dependencies

array=( "keytool" "openssl" )
for i in "${array[@]}"
do
    command -v $i >/dev/null 2>&1 || { 
        echo >&2 "$i required"; 
        exit 1; 
    }
done

# Generate truststore and keystore for Alfresco services
# Default password for "alfresco.truststore" is "truststore"
# Default password for "alfresco.keystore" is "keystore"

keytool -import -alias opensearch.ca -noprompt -file certs/ca/ca.pem \
-keystore alfresco.truststore -storetype PKCS12 -storepass truststore
keytool -import -alias opensearch.os1 -noprompt -file certs/os1/os1.pem \
-keystore alfresco.truststore -storetype PKCS12 -storepass truststore

openssl pkcs12 -export -out alfresco.keystore -inkey certs/ca/admin.key -in certs/ca/admin.pem -password pass:keystore
keytool -import -trustcacerts -noprompt -alias opensearch.ca -file certs/ca/ca.pem \
-keystore alfresco.keystore -storetype PKCS12 -storepass keystore