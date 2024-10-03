The project contains the following implementations:
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

**Running the Content Server**

4. mvn exec:java -Dexec.mainClass="org.example.ContentServer" -Dexec.args="localhost:4567 /Users/anamikacherukat/Documents/UoA/S2/DistributedSystems/Assignment2-a1930297-DS/src/main/java/org/example/textFilePath.txt

**Running GETClient**

5. mvn exec:java -Dexec.mainClass="org.example.GETClient" -Dexec.args="localhost:4567" -Dexec.classpathScope=compile

The test folder consists 3 test files -
1. AggregationServerTest
2. ContentServerTest
3. GETClientTest

Each of them contains unit tests for assessing the working of the project, 
including edge cases. 

