package com.rjrudin.marklogic.flexrep;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;

/**
 * Mimics the behavior of a file-based network guard with a directory for the master to write to and a directory for the
 * replica to write to.
 */
public class NetworkGuard {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private File fromMasterDir;
    private File fromReplicaDir;

    public NetworkGuard() {
        this(new File("network-guard"));
    }

    public NetworkGuard(File baseDir) {
        this.fromMasterDir = new File(baseDir, "from-master");
        this.fromMasterDir.mkdirs();
        this.fromReplicaDir = new File(baseDir, "from-replica");
        this.fromReplicaDir.mkdirs();
    }

    public void writeRequestFromMasterToFile(String xml, String flexrepId) throws IOException {
        File file = new File(fromMasterDir, flexrepId + ".xml");
        logger.info("Writing request file to: " + file.getAbsolutePath());
        FileCopyUtils.copy(xml.toString().getBytes(), file);
    }

    public void writeResponseFromReplicaToFile(String xml, File requestFile) throws IOException {
        File responseFile = new File(fromReplicaDir, "response-" + requestFile.getName());
        logger.info("Writing response file to: " + responseFile.getAbsolutePath());
        FileCopyUtils.copy(xml.toString().getBytes(), responseFile);
    }

    /**
     * Very crude implementation of waiting for a file to show up.
     * 
     * @param requestFile
     * @return
     */
    public File waitForResponseFileToShowUp(String flexrepId) {
        File responseFile = new File(fromReplicaDir, "response-" + flexrepId + ".xml");
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

}
