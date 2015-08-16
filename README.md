This project is a demonstration of how to proxy flexrep HTTP requests from a master MarkLogic database to a file, which can then be 
sent through something like a network guard. Here's the whole process:

1. The master database sends an HTTP request to the proxy (the test uses Spring Boot to expose an HTTP endpoint)
1. The proxy writes the HTTP request to a file, which would then be sent through a network guard, for example
1. The proxy then reads the HTTP request from a file (this would be a separate JVM on the other side of the network
guard, and it would poll a directory for the file)
1. The proxy uses the file to construct an HTTP request to the replica MarkLogic server
1. The proxy writes the HTTP response from the replica server to a file
1. The proxy then reads the response file (this would be back on the master side of the network guard)
1. The proxy then uses the contents of the response file to populate the HTTP response that is sent back to the master ML database

To try this out, do the following:

1. Configure a simple flexrep setup as defined in the ML docs. I created a "master-db" database and a "replica-db" database, 
and my HTTP flexrep server is on port 8051.
1. Test out the flexrep setup using qconsole to make sure it's working properly. 
1. Now fire up the TestApplication in this repository, ensuring that the ML connection settings in src/main/resources/application.properties
are correct (I just run it through Eclipse - you can run "gradle eclipse" to create the Eclipse project files).
1. Change the master flexrep config to send requests to port 8050 instead of 8051.
1. Insert a document using qconsole into the master database - it should replicate successfully, and you should see lots of
helpful logging showing exactly what happened.
