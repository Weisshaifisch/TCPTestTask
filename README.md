# TCPTestTask
A simple two-way communication between a client and a multithreaded server via TCP with RPC ability.

1. Get maven
https://maven.apache.org/download.cgi
2. Install Maven
https://maven.apache.org/install.html

3. Run

3.1. Using Maven
cd to the cloned directory with pom.xml
mvn clean
mvn compile

Start server:
mvn exec:java -Dexec.mainClass="server.MyServer" -Dexec.args="port_number"
Example: mvn exec:java -Dexec.mainClass="server.MyServer" -Dexec.args="32323"


Start client
mvn exec:java -Dexec.mainClass="main.MyClient" -Dexec.args="host port_number number_of_threads_per_client"
Example: mvn exec:java -Dexec.mainClass="main.MyClient" -Dexec.args="localhost 32323 10"

3.2. Using Eclipse
Import as an existing Maven project
Run MyServer as an Application first.
Then run MyClient as an Application.