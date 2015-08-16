package com.rjrudin.marklogic.flexrep;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

/**
 * Uses Spring Boot to fire up a Tomcat instance that can proxy HTTP requests from the master ML.
 */
@Controller
@EnableAutoConfiguration(exclude = { org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration.class })
public class TestApplication {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private FlexrepFileProxy flexrepProxy;

    @Value("${mlReplicaHost}")
    private String mlReplicaHost;

    @Value("${mlReplicaPort}")
    private int mlReplicaPort;

    @Value("${mlReplicaUsername}")
    private String mlReplicaUsername;

    @Value("${mlReplicaPassword}")
    private String mlReplicaPassword;

    public static void main(String[] args) throws Exception {
        SpringApplication.run(TestApplication.class, args);
    }

    @RequestMapping("/apply.xqy")
    @ResponseBody
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (flexrepProxy == null) {
            logger.info(String.format("Proxying requests to %s:%d", mlReplicaHost, mlReplicaPort));
            RestTemplate t = newRestTemplate(mlReplicaHost, mlReplicaPort, mlReplicaUsername, mlReplicaPassword);
            flexrepProxy = new FlexrepFileProxy(t, mlReplicaHost, mlReplicaPort);
        }
        flexrepProxy.proxy(request, response);
    }

    private RestTemplate newRestTemplate(String host, int port, String username, String password) {
        BasicCredentialsProvider prov = new BasicCredentialsProvider();
        prov.setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM), new UsernamePasswordCredentials(username,
                password));
        HttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(prov).build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
    }
}