JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java
 
 
CLASSES = \
	ShutdownThreadInterface.java \
	ShutdownThread.java \
	UserPass.java \
	offlineMsg.java \
	SimpleChatServer.java \
	SimpleChatClient.java \

default: classes
 
classes: $(CLASSES:.java=.class)
clean:
	$(RM) *.class