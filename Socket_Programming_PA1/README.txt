CSEE W4119 Computer Networks
Programming Assignment #1: Socket Programming

Su Shen
ss4716

Brief Descriptions:
I developed a relatively comprehensive multiple client-server application in Java. The functionalities 
include security, broadcasting, sending private message, obtaining the list of currently connected users 
and users logged in within one hour and logging out. In addition, I implemented a GUI in which users can 
enter commands instead of in the terminal. Also the users can send offline private message to a specific 
user and when the receiver log on, the offline messages will display on his console.

Running the code:
-> make 									/* Compile the code. */
-> java SimpleChatServer <port>				/* Run the server side application. */
-> java SimpleChatClient <IP> <port>		/* Run the client side application. */

Class description:
ShutdownThreadInterface.java: The interface to deal with ctrl+c appropriately. 
ShutdownThread.java: The class to deal with ctrl+c appropriately. 
UserPass.java: The class for server and clients to read user info. 
offlineMsg.java: The class to store offline messages for different users.
SimpleChatServer.java: The server class. It will start a new thread for every new logged on user.
SimpleChatClient.java: The client class.

Basic Functionalities:
This application implemented all required basic functionalities specified in the homework description file.

Extra Functionalities:
GUI: I used the Swing component to implement this part. It include: JTextField, JTextArea, JFrame, JPanel and etc.
Offline messages: This function can make user send offline private messages to another offline user and when the 
offline user log on, the messages will display on his console.							



