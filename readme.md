**Objective**


This project creates a RESTful API to enable secure and efficient communication between client and server applications through socket-based interactions. Adhering to REST architectural principles—such as a uniform interface and statelessness—the API utilizes JSON format for data handling. Clients can make GET and PUT requests to access and modify resources on the server, ensuring that update operations remain idempotent. The server manages each incoming request independently, which supports scalable communication among multiple clients. Detailed API documentation is provided to help developers effectively use the interface, promoting smooth integration within various business applications.

**Please Note** - The initial commits of this project was made on the main repository. Inorder to incorporate the changes after the draft submission a new branch has been created - **master**. Please consider this branch as the main for the rest of the project. 

It contains the following implementations:
- AggregationServer: The main server that accepts PUT and GET requests from clients and stores/retrieves weather data.
- ContentServer: The content servers that send weather data to the Aggregation Server.
- GETClient: A client that sends requests to retrieve weather data from the Aggregation Server.

The project utilises _Lamport_ Clocks for keeping a track on the order of the events.

 **Compilation**

To compile the source code, run the following command from the
project root directory -  _**Assignment2-a1930297-DS**_ :

1. mvn clean
2. mvn compile

**Running the Aggregation Server**
3. mvn exec:java -Dexec.mainClass="org.example.AggregationServer" -Dexec.classpathScope=compile

**On another terminal - Running the Content Server**

4. mvn exec:java -Dexec.mainClass="org.example.ContentServer" -Dexec.args="localhost:4567 /Users/anamikacherukat/Documents/UoA/S2/DistributedSystems/Assignment2-a1930297-DS/src/main/java/org/example/textFilePath.txt

**On another terminal - Running GETClient**

5. mvn exec:java -Dexec.mainClass="org.example.GETClient" -Dexec.args="localhost:4567" -Dexec.classpathScope=compile

The src/test folder consists of 3 test files -
1. AggregationServerTest
2. ContentServerTest
3. GETClientTest


Each of these test files have to be run manually by right clicking on the file name and choosing **Run Filename.java**. They can all be run at once by selecting **Run All** by right clicking on the test folder. 
Each of them contains unit tests for assessing the working of the project, 
including edge cases. 

