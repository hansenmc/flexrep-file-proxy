This project is a demonstration of how to proxy flexrep HTTP requests from a master MarkLogic database to a file, which can then be 
sent through something like a network guard. Here's the whole process:

1. The master database sends an HTTP request to the proxy (the test uses Spring Boot to expose an HTTP endpoint)
1. The proxy - the MasterProxy class - writes the HTTP request to a file, which could then be sent through a network guard, for example
1. A Camel instance on the other side of the presumed network guard then reads this file in and hands it off to the ReplicaProxy class
1. ReplicaProxy sends an HTTP request to the replica HTTP server based on the contents of the file.
1. ReplicaProxy writes the HTTP response from the replica HTTP server to a file, which would then be sent back across the presumed network guard.
1. MasterProxy checks every second for this response file to show it up; once it does, it uses the contents to populate the
HTTP response that is sent back to the master ML database, finishing replication of the document.

To try this out, do the following:

1. Configure a simple flexrep setup as defined in the ML docs. I created a "master-db" database and a "replica-db" database, 
and my HTTP flexrep server is on port 8077.
1. Test out the flexrep setup using qconsole to make sure it's working properly. 
1. Now fire up the TestApplication in this repository, which will start an instance of Tomcat that listens for requests
on port 8076 (I use Eclipse to run this - you can easily import this as an Eclipse project by first running "gradle eclipse"
to generate the project files).
1. Then run "gradle camel" to start up the Camel instance that talks to the replica HTTP server. Verif that the 
connection properties in src/main/resources/META-INF/camel-routes.xml are correct. 
1. Change the master flexrep config to send requests to port 8076 instead of 8077.
1. Insert a document using qconsole into the master database - it should replicate successfully, and you should see some
logging from both Tomcat and Camel. 
