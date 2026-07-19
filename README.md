# MessageQueue_java

compile: javac -d out *.java
run: 
    - ./gradlew run --args="broker"
    <!--Args of producer: producer port and topicId -->
    - ./gradlew run --args="producer 9936 1"
    <!--Args of consumer: consumer port, topicId, groupId -->
    - ./gradlew run --args="consumer 9836 1 0"