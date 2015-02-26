CSEE W4119 Computer Networks
Programming Assignment #3: Distributed Bellman-Ford

Su Shen
ss4716

Brief Descriptions:
In this assignment, I developed a simple version of the distributed Bellman-Ford algorithm. The algorithm will 
operate using a set of distributed client processes. Clients may be distributed across different machines and 
more than one client can be on the same machine.


Important Notes:
I basically implemented every required function specified in the instruction, which includes "SHOWRT", "CLOSE", 
"LINKDOWN" and "LINKUP". But I have three points need to clarify here.

1. When you are linking down one link on my program, it will take around 5 to 6 TIMEOUT for all the clients to 
converge to get the correct routing table. So after you link down, please wait for a while to execute the next 
command. Otherwise, the distance vectors displayed may not be correct. For "CLOSE" command, it will take 3 * 
TIMEOUT for its neighbors to discover that, so please wait patiently as well. And if you encounter some incorrect 
distance vectors, please re-enter the command you just entered. I tested in various topologies, the algorithms 
designed in the program should be mostly correct. It just take some time.

2. I include the client itself as a destination in the distance vector displayed, which the cost is 0 and 
destination and link is itself, because I believe it can make the client more easily identify its own address 
information.

3. When the node become inaccessible from current client, it will disappear from its distance vector instead of
showing a cost of infinity in there.


Running the code:
-> make 																				/* Compile the code. */
-> java bfclient <localport> <TIMEOUT> <neighborIP> <neighborPort> <cost>	......		/* Run the program.  */


Class description:
bfclient.java: The main program to execute all the functions.
NeighborInfo.java: The class storing the neighbors' distance vectors for each client.


Extra Functionalities:
GUI: I used the Swing component to implement this part. It include: JTextField, JTextArea, JFrame, JPanel and etc.
So the user can enter the command and see the corresponding output in the GUI window.


Designed Protocol for Inter-Client Communication:
The format of each distance vector I encode in each route update message is as follow:
<localIP>:<localPort> <destination IP>:<destination port>:<cost>:<first hop client IP>:<first hop client port> ......


