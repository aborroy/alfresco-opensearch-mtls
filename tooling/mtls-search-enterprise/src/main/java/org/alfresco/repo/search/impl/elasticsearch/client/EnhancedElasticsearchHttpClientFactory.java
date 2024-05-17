/*
 * #%L
 * Alfresco Enterprise Repository
 * %%
 * Copyright (C) 2005 - 2024 Alfresco Software Limited
 * %%
 * License rights for this program may be obtained from Alfresco Software, Ltd.
 * pursuant to a written agreement and any use of this program without such an
 * agreement is prohibited.
 * #L%
 */
package org.alfresco.repo.search.impl.elasticsearch.client;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.alfresco.encryption.AlfrescoKeyStore;
import org.alfresco.encryption.AlfrescoKeyStoreImpl;
import org.alfresco.encryption.KeyResourceLoader;
import org.alfresco.encryption.ssl.SSLEncryptionParameters;
import org.alfresco.error.AlfrescoRuntimeException;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class applies the patch described in https://hyland.atlassian.net/browse/ACS-7965
 * <p/>
 * Singleton factory for Elasticsearch Http Client.
 * This class is providing an Elastic RestHighLevelClient instance,
 * that maintains a pool of RestLowLevelClient instances.
 * <p/>
 * Client configuration is built according to "elasticsearch.secureComms" value:
 * - none: plain HTTP connection
 * - https: TLS connection using "encryption.ssl.truststore.*" truststore
 * - mtls: mTLS connection using "encryption.ssl.truststore.*" truststore and "encryption.ssl.keystore.*" keystore
 */
public class EnhancedElasticsearchHttpClientFactory extends ElasticsearchHttpClientFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchHttpClientFactory.class);

    // Elasticsearch Http Client connection pool
    private RestHighLevelClient client;

    // Basic parameters for Elasticsearch server endpoint
    private String host;
    private String baseUrl;
    private int port;

    // SSL parameters for Elasticsearch server endpoint
    private String secureComms;
    private AlfrescoKeyStore sslTrustStore;
    private SSLEncryptionParameters sslEncryptionParameters;
    private KeyResourceLoader keyResourceLoader;
    private boolean hostNameVerification;

    // mTLS parameters for Elasticsearch server endpoint
    private AlfrescoKeyStore sslKeyStore;

    // Http Basic Authentication credential parameters for Elasticsearch server endpoint
    private String user;
    private String password;

    // Elasticsearch index details
    private String indexName;
    private String archiveIndexName;

    // Connection pool size
    private int maxTotalConnections;
    private int maxHostConnections;
    private int threadCount;

    // Connection and request timeout
    private int connectionTimeout;
    private int socketTimeout;

    /**
     * Initialize SSL Truststore for https and mtls connections using
     * "encryption.ssl.truststore.*" properties
     * Initialize SSL Keystore for mtls connections using
     * "encryption.ssl.keystore.*" properties
     */
    public void init()
    {
        this.sslTrustStore = new AlfrescoKeyStoreImpl(sslEncryptionParameters.getTrustStoreParameters(),
                keyResourceLoader);
        this.sslKeyStore = new AlfrescoKeyStoreImpl(sslEncryptionParameters.getKeyStoreParameters(),
                keyResourceLoader);
    }

    /**
     * Singleton method returning the Elasticsearch client.
     * The client is only built if it's not already created.
     *
     * @return Elasticsearch client
     */
    public RestHighLevelClient getElasticsearchClient()
    {
        if (client == null)
        {
            LOGGER.debug("Creating Elasticsearch client for {}",
                    (secureComms.equals("none") ? "http" : "https") + "://" + host + ":" + port + baseUrl);
            client = getElasticsearchClient(secureComms.equals("none") ? "http" : "https", port);
        }
        return client;
    }

    /**
     * Gets Elasticsearch server URL
     * @return Elasticsearch server URL
     */
    public String getElasticsearchServerUrl()
    {
        return (secureComms.equals("none") ? "http" : "https") + "://" + host + ":" + port + baseUrl;
    }

    /**
     * Creates an Elasticsearch client applying parameters from properties file
     *
     * @param protocol Http protocol: http or https
     * @param port Port number
     * @return Elasticsearch client ready to be used
     */
    private RestHighLevelClient getElasticsearchClient(String protocol, int port)
    {
        return new RestHighLevelClient(RestClient.builder(new HttpHost(host, port, protocol))
                .setHttpClientConfigCallback(this::getHttpAsyncClientBuilder).setPathPrefix(baseUrl));
    }

    /**
     * Apply timeout settings to Rest Client
     *
     * @return Rest Client configuration
     */
    private RequestConfig getRequestConfigBuilder()
    {
        return RequestConfig.custom().setConnectTimeout(connectionTimeout).setSocketTimeout(socketTimeout).build();
    }

    /**
     * Apply pooling options, credentials and SSL settings to Elasticsearch client
     *
     * @param httpClientBuilder Existing HttpClientBuilder instance
     * @return httpClientBuilder including required settings
     */
    private HttpAsyncClientBuilder getHttpAsyncClientBuilder(HttpAsyncClientBuilder httpClientBuilder)
    {
        httpClientBuilder.setMaxConnTotal(maxTotalConnections).setMaxConnPerRoute(maxHostConnections)
                .setDefaultRequestConfig(getRequestConfigBuilder());
        // Credentials have been given, so pass them wrapped in a CredentialsProvider
        if (user != null && !user.equals(""))
        {
            httpClientBuilder.setDefaultCredentialsProvider(getCredentialsProvider());
        }
        // Secure http mode has been selected, so build the SSLContext with the right truststore
        if (secureComms.equals("https"))
        {
            httpClientBuilder.setSSLContext(getSSLContext());
            if (!hostNameVerification)
            {
                httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
            }
        }
        // mTLS mode has been selected, so build the SSLContext with the right keystore and truststore
        if (secureComms.equals("mtls")) {
            httpClientBuilder.setSSLContext(getSSLContextForMtls());
            if (!hostNameVerification)
            {
                httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
            }
        }
        // Override the default thread count unless it's either undefined or invalid
        if (threadCount > 0)
        {
            httpClientBuilder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(threadCount).build());
        }
        else
        {
            LOGGER.debug("Using default ioThreadCount for Elasticsearch HTTP Client since the specified value was {}.",
                    threadCount);
        }

        return httpClientBuilder;
    }

    /**
     * Build CredentialsProvider instance with user and password values from properties file
     *
     * @return CredentialsProvider instance
     */
    private CredentialsProvider getCredentialsProvider()
    {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
        return credentialsProvider;
    }

    /**
     * Build SSLContext instance with truststore that must include Elasticsearch server
     * public certificate in order to be trusted for this https connection.
     *
     * @return SSLContext instnce
     */
    private SSLContext getSSLContext()
    {
        TrustManager[] trustmanagers = sslTrustStore.createTrustManagers();

        try
        {
            SSLContext sslcontext = SSLContext.getInstance("TLSv1.2");
            sslcontext.init(null, trustmanagers, null);
            return sslcontext;
        }
        catch (Exception e)
        {
            throw new AlfrescoRuntimeException("Unable to create SSL context", e);
        }
    }

    /**
     * Build SSLContext instance with truststore that must include Elasticsearch server
     * public certificate in order to be trusted for this https connection and a
     * private certificate to encrypt connection.
     *
     * @return SSLContext instance
     */
    private SSLContext getSSLContextForMtls()
    {
        KeyManager[] keyManagers = sslKeyStore.createKeyManagers();
        TrustManager[] trustmanagers = sslTrustStore.createTrustManagers();

        try
        {
            SSLContext sslcontext = SSLContext.getInstance("TLSv1.2");
            sslcontext.init(keyManagers, trustmanagers, null);
            return sslcontext;
        }
        catch (Exception e)
        {
            throw new AlfrescoRuntimeException("Unable to create SSL context", e);
        }
    }


    public void setHost(String host)
    {
        this.host = host;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public void setSecureComms(String secureComms)
    {
        this.secureComms = secureComms;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public void setMaxTotalConnections(int maxTotalConnections)
    {
        this.maxTotalConnections = maxTotalConnections;
    }

    public void setMaxHostConnections(int maxHostConnections)
    {
        this.maxHostConnections = maxHostConnections;
    }

    public void setConnectionTimeout(int connectionTimeout)
    {
        this.connectionTimeout = connectionTimeout;
    }

    public void setSocketTimeout(int socketTimeout)
    {
        this.socketTimeout = socketTimeout;
    }

    public void setSslEncryptionParameters(SSLEncryptionParameters sslEncryptionParameters)
    {
        this.sslEncryptionParameters = sslEncryptionParameters;
    }

    public void setKeyResourceLoader(KeyResourceLoader keyResourceLoader)
    {
        this.keyResourceLoader = keyResourceLoader;
    }

    public void setHostNameVerification(boolean hostNameVerification)
    {
        this.hostNameVerification = hostNameVerification;
    }

    public void setIndexName(String indexName)
    {
        this.indexName = indexName;
    }

    public void setArchiveIndexName(String archiveIndexName) {
        this.archiveIndexName = archiveIndexName;
    }

    public String getArchiveIndexName() {
        return archiveIndexName;
    }

    public String getIndexName()
    {
        return indexName;
    }

    public void setThreadCount(int threadCount)
    {
        this.threadCount = threadCount;
    }

}