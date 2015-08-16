package com.rjrudin.marklogic.flexrep;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Example of receiving an HTTP request from a master ML; writing it to file; reading it back in and making an HTTP
 * request to a replica ML; writing the response to file; then reading the response back in and returning that to the
 * master ML.
 */
public class FlexrepFileProxy {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private RestTemplate restTemplate;
    private String host;
    private int port;

    private DocumentBuilderFactory docBuilderFactory;

    public FlexrepFileProxy(RestTemplate restTemplate, String host, int port) {
        this.restTemplate = restTemplate;
        this.host = host;
        this.port = port;
        this.docBuilderFactory = DocumentBuilderFactory.newInstance();
    }

    public void proxy(HttpServletRequest request, HttpServletResponse response) throws Exception {

        // Write the master HTTP request to a file
        File requestFile = writeRequestToFile(request);

        // Read file back in and send request via RestTemplate, then write the response to a file
        File responseFile = sendRequestToReplica(requestFile);

        // Read file back in and write as response to original request
        returnResponseToMaster(responseFile, response);
    }

    /**
     * For now, just using string concatention to create a simple XML document that contains all of the HTTP headers and
     * the HTTP body.
     * 
     * @param request
     * @return
     * @throws IOException
     */
    private File writeRequestToFile(HttpServletRequest request) throws IOException {
        StringBuilder xml = new StringBuilder("<flexrep-request><headers>");
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            xml.append("<header>");
            String name = names.nextElement();
            xml.append("<name>").append(name).append("</name>");
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                xml.append("<value>").append(values.nextElement()).append("</value>");
            }
            xml.append("</header>");
        }
        xml.append("</headers>");

        xml.append("<body>");
        String body = new String(FileCopyUtils.copyToByteArray(request.getInputStream()));
        xml.append(escapeBody(body)).append("</body></flexrep-request>");

        String id = getFlexrepId(request);
        File file = new File("build", id + ".xml");
        FileCopyUtils.copy(xml.toString().getBytes(), file);
        return file;
    }

    /**
     * The flexrep ID is used for constructing a filename to save the initial HTTP request to.
     * 
     * @param request
     * @return
     */
    private String getFlexrepId(HttpServletRequest request) {
        String type = request.getHeader("Content-type");
        return type.replace("multipart/flexible-replication; boundary=", "");
    }

    /**
     * Read the file in and use its contents to construct an HTTP request to send to the replica server.
     * 
     * @param file
     * @throws Exception
     */
    private File sendRequestToReplica(File file) throws Exception {
        String url = String.format("http://%s:%d/apply.xqy", host, port);
        return restTemplate.execute(url, HttpMethod.POST, new RequestCallback() {
            @Override
            public void doWithRequest(ClientHttpRequest request) throws IOException {
                try {
                    copyFileDataToRequest(file, request);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, new ResponseExtractor<File>() {
            @Override
            public File extractData(ClientHttpResponse response) throws IOException {
                return writeReplicaResponseToFile(file, response);
            }
        });
    }

    /**
     * Write the replica HTTP response to a simple XML file.
     * 
     * TODO Test for errors.
     * 
     * @param requestFile
     * @param response
     * @return
     * @throws IOException
     */
    private File writeReplicaResponseToFile(File requestFile, ClientHttpResponse response) throws IOException {
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
        xml.append(escapeBody(body)).append("</body></flexrep-response>");

        File responseFile = new File("build", "response-" + requestFile.getName());
        FileCopyUtils.copy(xml.toString().getBytes(), responseFile);
        return responseFile;
    }

    private void returnResponseToMaster(File responseFile, HttpServletResponse response) throws Exception {
        Document doc = docBuilderFactory.newDocumentBuilder().parse(responseFile);
        NodeList headerNodes = doc.getDocumentElement().getElementsByTagName("headers").item(0).getChildNodes();
        for (int i = 0; i < headerNodes.getLength(); i++) {
            Element header = (Element) headerNodes.item(i);
            String name = header.getFirstChild().getTextContent();
            NodeList valueNodes = header.getElementsByTagName("value");
            for (int j = 0; j < valueNodes.getLength(); j++) {
                String value = valueNodes.item(j).getTextContent();
                logger.debug("Adding master response header: " + name + "; values: " + value);
                response.addHeader(name, value);
            }
        }

        String body = doc.getDocumentElement().getElementsByTagName("body").item(0).getTextContent();
        body = unescapeBody(body);
        if (logger.isDebugEnabled()) {
            logger.debug("Writing replica response body to master response: " + body);
        }
        FileCopyUtils.copy(body.getBytes(), response.getOutputStream());
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
            logger.debug("Adding replica request header: " + name + "; values: " + values);
            request.getHeaders().put(name, values);
        }

        String body = doc.getDocumentElement().getElementsByTagName("body").item(0).getTextContent();
        body = unescapeBody(body);
        if (logger.isDebugEnabled()) {
            logger.debug("Writing master request body to replica request: " + body);
        }
        FileCopyUtils.copy(body.getBytes(), request.getBody());
    }

    /**
     * In addition to escaping certain XML characters, we need to replace a carriage return + a line feed, because when
     * it's read back in, only the line feed is preserved. But flexrep isn't happy with the multipart data unless the
     * carriage returns are there as well.
     * 
     * @param body
     * @return
     */
    private String escapeBody(String body) {
        body = body.replace("<", "&lt;");
        body = body.replace(">", "&gt;");
        body = body.replace("\r\n", "CARRIAGERETURN\n");
        return body;
    }

    /**
     * Unescape XML characters, and also replace our special token for preserving a carriage return followed by a line
     * feed.
     * 
     * @param body
     * @return
     */
    private String unescapeBody(String body) {
        body = body.replace("&lt;", "<");
        body = body.replace("&gt;", ">");
        body = body.replace("CARRIAGERETURN\n", "\r\n");
        return body;
    }
}
