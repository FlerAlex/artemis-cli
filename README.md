## **artemis-cli \- User Documentation**

artemis-cli is a high-performance, command-line interface for interacting with an Apache ActiveMQ Artemis message broker. Built with Scala 3 and Cats Effect, it provides a simple, type-safe interface for publishing to and subscribing from JMS destinations.

The tool is designed for both interactive use and for integration into automated scripts and testing workflows.

### **1\. Getting Started**

#### **Prerequisites**

Before you begin, ensure you have the following installed on your system:

* **Git:** For cloning the repository.  
* **Java Development Kit (JDK):** Version 11 or higher.  
* **sbt:** The Scala Build Tool, required to build the application from source.  
* **Docker and Docker Compose:** Required for running the local development environment.

#### **Step 1: Clone the Repository**

First, clone the project repository to your local machine:

git clone https://github.com/FlerAlex/artemis-cli.git  
cd artemis-cli

#### **Step 2: Local Development & Testing with Docker**

This project includes a docker-compose.yaml file to easily spin up a complete testing environment, including a 5-node Artemis cluster and two client containers.

* Start the Environment  
  From the root of the project, run the following command to start all services in the background:  
  docker-compose up \-d

  This will start:  
  * Five Artemis broker containers (artemis1 to artemis5).  
  * Two client containers (client1, client2) with a Java runtime, which will be used to run the artemis-cli tool.  
* Build and Deploy the Application  
  The basicCI.sh script automates the process of building the application and deploying it to the running client containers. From the root of the project, run the script:  
  ./basicCI.sh

  This script will:  
  1. Compile the Scala code.  
  2. Build the executable "fat JAR".  
  3. Copy the JAR, the wrapper script (artemis-cli), and the configuration files (application.conf, logback.xml) into both the client1 and client2 Docker containers.  
* Run Commands in a Client Container  
  You can now execute artemis-cli commands from within either of the client containers. To open a shell inside the client1 container, run:  
  docker exec \-it client1 /bin/bash

  Once inside the container, you can navigate to the /app directory and run commands as described in the "Usage" section below.

### **2\. Configuration**

The application requires a configuration file to specify the broker connection details.

**Example config/application.conf:**

\# config/application.conf

app {  
  \# The full URL for the Artemis broker cluster.  
  \# The client will attempt to connect to the hosts in the provided list  
  \# for high availability.  
  broker-url \= "(tcp://artemis1:61616,tcp://artemis2:61617,tcp://artemis3:61618)"

  \# Credentials for connecting to the broker  
  credentials {  
    user \= "admin"  
    pass \= "admin"  
  }  
}

You must provide the path to this file every time you run the application using the global \--config option.

### **3\. Usage**

The application is controlled via the artemis-cli wrapper script with two primary subcommands: publish and subscribe.

#### **Global Options**

* \--config \<path\>, \-c \<path\>  
  * **Required.** Specifies the path to your application.conf file.

#### **publish Subcommand**

Sends messages to a specified destination.

* **Options:**  
  * \--destination \<name\>, \-d \<name\>: **(Required)** The destination name.  
  * \--type \<type\>, \-t \<type\>: **(Required)** topic or queue.  
  * \--messages \<number\>, \-m \<number\>: The number of messages to send. (Default: 1\)  
  * \--payload \<json\_string\>: The message body as a JSON string.

#### **subscribe Subcommand**

Subscribes to a topic with intelligent consumption logic.

* **Options:**  
  * \--name \<sub\_name\>, \-n \<sub\_name\>: **(Required)** A unique name for the durable subscriber.  
  * \--topic \<topic\_name\>: The name of the topic to subscribe to.  
  * \--last-value: If present, enables last-value, non-destructive semantics.  
  * \--timeout \<seconds\>: Time in seconds to wait for a message. (Default: 5\)

### **4\. Use Cases**

⭐ **Important Note on Topics & Durable Subscriptions** ⭐

For a durable subscriber to receive messages, its subscription **must exist on the broker before the messages are published**. The artemis-cli subscribe command creates this subscription on its first run. If you publish messages to a topic before any subscriber has connected, those messages will be lost because the broker has no queue in which to hold them for that subscriber.

**You must always run the subscriber first to ensure the destination is ready.**

#### **Be Aware: Subscription Name Collisions**

If you attempt to subscribe to a new topic using a \--name that is already associated with a different topic, you will receive an error similar to AMQ229082: Queue client-1.client-1 already exists on another subscription.

This happens because a single durable subscription name (e.g., client-1) can only be bound to one topic at a time.

**Recommendation:** Always use a new, unique \--name when subscribing to a different topic. For example, if you have a subscriber named client-1 on TopicA and want to subscribe to TopicB, use a new name like client-1-topicB.

#### **Use Case 1: Standard Durable Subscription with JSON**

This scenario demonstrates publishing structured JSON data to a standard durable subscriber.

1. **Start the Subscriber First (in client1)**: This step is critical as it creates the durable subscription on the broker, ready to receive messages.  
   docker exec \-it client1 /bin/bash  
   cd /app  
   ./artemis-cli \-c config/application.conf subscribe \--name client-1 \--topic DurableNewsTopic

2. **Publish a JSON Message (in client2)**: Open a **second** terminal. Use the \--payload option to send a JSON object.  
   docker exec \-it client2 /bin/bash  
   cd /app  
   ./artemis-cli \-c config/application.conf publish \--destination DurableNewsTopic \--type topic \\  
     \--payload '{"eventId": "evt-001", "status": "processed"}'

3. **Observe**: The client1 terminal will display the full JSON string, showing that the structured message was received.  
   Received: '{"eventId": "evt-001", "status": "processed"}'

#### **Use Case 2: Last-Value Topic with JSON (Late Joiner)**

This scenario shows how a "late-joining" subscriber can receive the last known state, sent as a JSON object.

1. **Start the First Subscriber (in client1)**: This action configures the topic on the broker to retain the last value.  
   docker exec \-it client1 /bin/bash  
   cd /app  
   ./artemis-cli \-c config/application.conf subscribe \--name lv-client-1 \--topic SensorStatus \--last-value

2. **Publish a JSON Status Message (in client2)**:  
   docker exec \-it client2 /bin/bash  
   cd /app  
   ./artemis-cli \-c config/application.conf publish \--destination SensorStatus \--type topic \\  
     \--payload '{"sensorId": "temp-001", "status": "ONLINE", "value": 72.5}'

   The first subscriber receives the message, and the broker retains it as the "last value".  
3. **Start a Second Subscriber (in a new terminal)**:  
   docker exec \-it client2 /bin/bash  \# Or client1  
   cd /app  
   ./artemis-cli \-c config/application.conf subscribe \--name lv-client-2 \--topic SensorStatus \--last-value

4. **Observe**: The second subscriber will **immediately** receive the JSON payload representing the last known status.  
   Received: '{"sensorId": "temp-001", "status": "ONLINE", "value": 72.5}'

#### **Use Case 3: Point-to-Point Queue**

This demonstrates sending messages to a queue. Unlike topics, queues hold messages even if no consumer is present.

1. **Publish to a Queue**:  
   docker exec \-it client1 /bin/bash  
   cd /app  
   ./artemis-cli \-c config/application.conf publish \--messages 10 \--destination MyTestQueue \--type queue

2. **Result**: The messages are now stored in MyTestQueue on the broker. (Note: The subscribe command in this application is designed for topics and cannot be used to consume from a point-to-point queue).