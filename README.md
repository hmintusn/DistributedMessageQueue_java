# MessageQueue_java

compile: javac -d out *.java
run: 
    - java -cp out Application broker
    - java -cp out Application producer 9936 1