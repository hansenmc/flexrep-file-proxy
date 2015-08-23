package com.rjrudin.marklogic.flexrep;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.camel.Handler;
import org.apache.camel.Message;
import org.apache.camel.component.file.GenericFile;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This class is responsible for receiving a file from Camel, making an HTTP request to the ML replica, and then writing
 * the HTTP response out to a file.
 */
public class ReplicaProxy implements InitializingBean {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private RestTemplate restTemplate;
    private NetworkGuard networkGuard;

    private String host;
    private int port;
    private String username;
    private String password;

    private DocumentBuilderFactory docBuilderFactory;

    public ReplicaProxy() {
        this.docBuilderFactory = DocumentBuilderFactory.newInstance();
        this.networkGuard = new NetworkGuard();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.restTemplate = newRestTemplate(host, port, username, password);
    }

    @Handler
    public void handleMessage(Message message) throws Exception {
        GenericFile<?> file = (GenericFile<?>) message.getBody();
        sendRequestToReplica((File) file.getFile());
    }

    /**
     * Read the file in and use its contents to construct an HTTP request to send to the replica server.
     * 
     * @param file
     * @throws Exception
     */
    public void sendRequestToReplica(File file) throws Exception {
        String url = String.format("http://%s:%d/apply.xqy", host, port);
        restTemplate.execute(url, HttpMethod.POST, new RequestCallback() {
            @Override
            public void doWithRequest(ClientHttpRequest request) throws IOException {
                try {
                    copyFileDataToRequest(file, request);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ResponseExtractor<Void>() {
            @Override
            public Void extractData(ClientHttpResponse response) throws IOException {
                logger.info(String.format("Processing replica HTTP response with status %s", response.getStatusText()));
                writeReplicaResponseToFile(file, response);
                return null;
            }
        });
    }

    /**
     * Write the replica HTTP response to a simple XML file.
     * 
     * @param requestFile
     * @param response
     * @return
     * @throws IOException
     */
    private void writeReplicaResponseToFile(File requestFile, ClientHttpResponse response) throws IOException {
        StringBuilder xml = new StringBuilder("<flexrep-response><headers>");
        HttpHeaders headers = response.getHeaders();
        for (String key : headers.keySet()) {
            xml.append("<header><name>").append(key).append("</name>");
            for (String val : headers.get(key)) {
                xml.append("<value>").append(val).append("</value>");
            }
            xml.append("</header>");
        }
        xml.append("</headers>");

        xml.append("<body>");
        String body = new String(FileCopyUtils.copyToByteArray(response.getBody()));
        xml.append(XmlUtil.escapeBody(body)).append("</body></flexrep-response>");

        networkGuard.writeResponseFromReplicaToFile(xml.toString(), requestFile);
    }

    /**
     * Use crummy JAXP APIs to read in XML (only using JAXP because it doesn't introduce any dependencies).
     * 
     * @param requestFile
     * @param request
     * @throws Exception
     */
    private void copyFileDataToRequest(File requestFile, ClientHttpRequest request) throws Exception {
        Document doc = docBuilderFactory.newDocumentBuilder().parse(requestFile);
        NodeList headerNodes = doc.getDocumentElement().getElementsByTagName("headers").item(0).getChildNodes();
        for (int i = 0; i < headerNodes.getLength(); i++) {
            Element header = (Element) headerNodes.item(i);
            String name = header.getFirstChild().getTextContent();
            List<String> values = new ArrayList<String>();
            NodeList valueNodes = header.getElementsByTagName("value");
            for (int j = 0; j < valueNodes.getLength(); j++) {
                values.add(valueNodes.item(j).getTextContent());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Adding replica request header: " + name + "; values: " + values);
            }
            request.getHeaders().put(name, values);
        }

        String body = doc.getDocumentElement().getElementsByTagName("body").item(0).getTextContent();
        body = XmlUtil.unescapeBody(body);
        if (logger.isDebugEnabled()) {
            logger.debug("Writing master request body to replica request: " + body);
        }
        FileCopyUtils.copy(body.getBytes(), request.getBody());
    }

    private RestTemplate newRestTemplate(String host, int port, String username, String password) {
        BasicCredentialsProvider prov = new BasicCredentialsProvider();
        prov.setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM), new UsernamePasswordCredentials(username,
                password));
        HttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(prov).build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
