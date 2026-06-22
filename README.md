# MessageQueue_java

compile: javac -d out *.java
run: 
    - java -cp out Application broker
    <!--Args of producer: producer port and topicId -->
    - java -cp out Application producer 9936 1
    <!--Args of consumer: consumer port, topicId, groupId -->
    - java -cp out Application consumer 9836 1 0