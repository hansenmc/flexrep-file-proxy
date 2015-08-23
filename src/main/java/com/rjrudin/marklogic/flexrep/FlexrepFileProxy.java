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
 * Example of receiving an HTTP request from a master ML; writing it to file; reading it back in and making an HTTP
 * request to a replica ML; writing the response to file; then reading the response back in and returning that to the
 * master ML.
 */
public class FlexrepFileProxy {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private DocumentBuilderFactory docBuilderFactory;

    public FlexrepFileProxy() {
        this.docBuilderFactory = DocumentBuilderFactory.newInstance();
    }

    public void proxy(HttpServletRequest request, HttpServletResponse response) throws Exception {
        File requestFile = writeHttpRequestToFile(request);
        File responseFile = waitForResponseFileToShowUp(requestFile);
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
    private File writeHttpRequestToFile(HttpServletRequest request) throws IOException {
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
        File file = new File("network-guard/to-replica", id + ".xml");
        logger.info("Writing request file to: " + file.getAbsolutePath());
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
     * Very crude implementation of waiting for a file to show up.
     * 
     * @param requestFile
     * @return
     */
    private File waitForResponseFileToShowUp(File requestFile) {
        File dir = new File("network-guard/from-replica");
        File responseFile = new File(dir, "response-" + requestFile.getName());
        while (!responseFile.exists()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Waiting for file to show up: " + responseFile.getAbsolutePath());
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                // Ignore
            }
        }
        return responseFile;
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
        body = unescapeBody(body);
        if (logger.isDebugEnabled()) {
            logger.debug("Writing replica response body to master response: " + body);
        }
        FileCopyUtils.copy(body.getBytes(), response.getOutputStream());
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
