package com.rjrudin.marklogic.flexrep;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This class acts as a proxy to the master ML instance. It receives an HTTP request from the master, writes it to a
 * file, waits for a response file to show up, and then sends an HTTP response back to the master.
 */
public class MasterProxy {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private DocumentBuilderFactory docBuilderFactory;
    private NetworkGuard networkGuard;

    public MasterProxy() {
        this.docBuilderFactory = DocumentBuilderFactory.newInstance();
        this.networkGuard = new NetworkGuard();
    }

    public void proxy(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String flexrepId = writeHttpRequestToFile(request);
        File responseFile = networkGuard.waitForResponseFileToShowUp(flexrepId);
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
    private String writeHttpRequestToFile(HttpServletRequest request) throws IOException {
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
        xml.append(XmlUtil.escapeBody(body)).append("</body></flexrep-request>");

        String id = getFlexrepId(request);
        networkGuard.writeRequestFromMasterToFile(xml.toString(), id);
        return id;
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
     * Read in the response file and populate the headers and body of the master HTTP response.
     * 
     * @param responseFile
     * @param response
     * @throws Exception
     */
    private void returnResponseToMaster(File responseFile, HttpServletResponse response) throws Exception {
        Document doc = docBuilderFactory.newDocumentBuilder().parse(responseFile);
        NodeList headerNodes = doc.getDocumentElement().getElementsByTagName("headers").item(0).getChildNodes();
        for (int i = 0; i < headerNodes.getLength(); i++) {
            Element header = (Element) headerNodes.item(i);
            String name = header.getFirstChild().getTextContent();
            NodeList valueNodes = header.getElementsByTagName("value");
            for (int j = 0; j < valueNodes.getLength(); j++) {
                String value = valueNodes.item(j).getTextContent();
                if (logger.isDebugEnabled()) {
                    logger.debug("Adding master response header: " + name + "; values: " + value);
                }
                response.addHeader(name, value);
            }
        }

        String body = doc.getDocumentElement().getElementsByTagName("body").item(0).getTextContent();
        body = XmlUtil.unescapeBody(body);
        if (logger.isDebugEnabled()) {
            logger.debug("Writing replica response body to master response: " + body);
        }
        FileCopyUtils.copy(body.getBytes(), response.getOutputStream());
    }
}
