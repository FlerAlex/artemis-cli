# **Artemis CLI \- User Documentation**

artemis-cli is a powerful, type-safe command-line application for interacting with an ActiveMQ Artemis message broker. Built with Scala 3 and Cats Effect, it provides a simple interface for publishing to and subscribing from JMS destinations.

## **1\. Getting Started**

### **Prerequisites**

Before you begin, ensure you have the following installed on your system:

* **Git**: For cloning the repository.  
* **Java Development Kit (JDK)**: Version 11 or higher.  
* **sbt**: The Scala Build Tool, required to build the application from source.  
* **Docker and Docker Compose**: Required for running the local development environment.

### **Step 1: Clone the Repository**

First, clone the project repository to your local machine:

git clone https://github.com/FlerAlex/artemis-cli.git  
cd artemis-cli

## **2\. Local Development & Testing with Docker**

This project includes a docker-compose.yaml file to easily spin up a complete testing environment, including a 5-node Artemis cluster and two client containers.

### **Step 1: Start the Environment**

From the root of the project, run the following command to start all services in the background:

docker-compose up \-d

This will start:

* Five Artemis broker containers (artemis1 to artemis5).  
* Two client containers (client1, client2) with a Java runtime, which will be used to run the artemis-cli tool.

### **Step 2: Build and Deploy the Application**

The basicCI.sh script automates the process of building the application and deploying it to the running client containers.

From the root of the project, run the script:

./basicCI.sh

This script will:

1. Compile the Scala code.  
2. Build the executable "fat JAR".  
3. Copy the JAR, the wrapper script (artemis-cli.sh), and the configuration files (application.conf, logback.xml) into both the client1 and client2 Docker containers.

### **Step 3: Run Commands in a Client Container**

You can now execute artemis-cli commands from within either of the client containers.

To open a shell inside the client1 container, run:

docker exec \-it client1 /bin/bash

Once inside the container, you can navigate to the app directory and run commands as described in the "Usage" section below.

\# Inside the client1 container  
cd /app  
./artemis-cli publish \--destination MyTopic \--type topic \--messages 5

## **3\. Usage**

The application is controlled via the artemis-cli wrapper script with two primary subcommands: publish and subscribe.

### **publish Subcommand**

Sends messages to a specified destination.

**Usage:** ./artemis-cli publish \[options\]

| Option | Short | Description | Default |
| :---- | :---- | :---- | :---- |
| \--destination | \-d | **(Required)** The destination name. | N/A |
| \--type | \-t | **(Required)** topic or queue. | N/A |
| \--messages | \-m | The number of messages to send. | 1 |

### **subscribe Subcommand**

Subscribes to a topic and waits for a single message or until a timeout is reached.

**Usage:** ./artemis-cli subscribe \[options\]

| Option | Short | Description | Default |
| :---- | :---- | :---- | :---- |
| \--name | \-n | **(Required)** A unique name for the durable subscriber. | N/A |
| \--topic |  | The name of the topic to subscribe to. | DurableNewsTopic |
| \--last-value |  | If present, enables last-value, non-destructive semantics. | false |
| \--timeout |  | Time in seconds to wait for a message before exiting. | 5 |

## **4\. Use Cases**

### **⭐ Important Note on Topics & Durable Subscriptions ⭐**

For a durable subscriber to receive messages, its subscription **must exist on the broker before the messages are published**. The artemis-cli subscribe command creates this subscription on its first run.

**If you publish messages to a topic before any subscriber has connected to create the durable subscription, those messages will be lost.** The broker has no queue in which to hold them for that subscriber. You must always run the subscriber first to ensure the destination is ready.

### **Use Case 1: Standard Durable Subscription**

This scenario demonstrates the required order of operations for a standard publish-subscribe model.

1. **Start the Subscriber First (in client1)**: Open a terminal and shell into client1. This step is critical as it creates the durable subscription on the broker.  
   docker exec \-it client1 /bin/bash  
   cd /app  
   ./artemis-cli subscribe \--name client-1 \--topic DurableNewsTopic

2. **Publish Messages (in client2)**: Open a *second* terminal and shell into client2.  
   docker exec \-it client2 /bin/bash  
   cd /app  
   ./artemis-cli publish \--messages 5 \--destination DurableNewsTopic \--type topic

3. **Observe**: You will see the "Processing message..." logs appear in the client1 terminal.

### **Use Case 2: Last-Value Topic (Late Joiner)**

This scenario shows how a new subscriber can immediately receive the last known message, but still requires a subscriber to connect first to establish the topic's special behavior.

1. **Start the First Subscriber (in client1)**: This action **configures the topic on the broker** to be a non-destructive, last-value topic.  
   docker exec \-it client1 /bin/bash  
   cd /app  
   ./artemis-cli subscribe \--name lv-client-1 \--topic ImportantTopic \--last-value

2. **Publish a Message (in client2)**:  
   docker exec \-it client2 /bin/bash  
   cd /app  
   ./artemis-cli publish \--messages 1 \--destination ImportantTopic \--type topic

   The first subscriber will receive the message, and it will be retained by the broker.  
3. **Start a Second Subscriber (in a new terminal)**:  
   docker exec \-it client2 /bin/bash \# Or client1  
   cd /app  
   ./artemis-cli subscribe \--name lv-client-2 \--topic ImportantTopic \--last-value

4. **Observe**: The second subscriber will **immediately** receive and process the last message that was sent.

### **Use Case 3: Point-to-Point Queue**

This demonstrates sending messages to a queue. Unlike topics, queues hold messages even if no consumer is present.

1. **Publish to a Queue**:  
   docker exec \-it client1 /bin/bash  
   cd /app  
   ./artemis-cli publish \--messages 10 \--destination MyTestQueue \--type queue

   The messages are now stored in MyTestQueue on the broker. (Note: The subscribe command in this application is designed for topics and cannot be used to consume from a point-to-point queue).
