package com.rjrudin.marklogic.flexrep;

public abstract class XmlUtil {

    /**
     * In addition to escaping certain XML characters, we need to replace a carriage return + a line feed, because when
     * it's read back in, only the line feed is preserved. But flexrep isn't happy with the multipart data unless the
     * carriage returns are there as well.
     * 
     * @param body
     * @return
     */
    public static String escapeBody(String body) {
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
    public static String unescapeBody(String body) {
        body = body.replace("&lt;", "<");
        body = body.replace("&gt;", ">");
        body = body.replace("CARRIAGERETURN\n", "\r\n");
        return body;
    }

}
