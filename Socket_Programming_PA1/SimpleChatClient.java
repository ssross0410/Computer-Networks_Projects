import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class SimpleChatClient implements ShutdownThreadInterface{	
	
	// a shutdown hook to shut down the client 
	private  ShutdownThread fShutdownThread;
	
	Socket sock;	// client socket
	BufferedReader reader;
	PrintWriter writer;
	
	// GUI components
	JTextArea incoming;
	JTextField outgoing;
	
	String userName;
	int userIndex;	
	int failedTimes;
	boolean loginFlag;
	
	// scan the user info
	UserPass user_pass = new UserPass();
	
	public static void main(String[] args) {
		if (args.length != 2) { 
			System.out.println("Command line arguments are not in correct format!"); 
		}
		else {
			SimpleChatClient client = new SimpleChatClient();	 
			client.go(args[0],Integer.parseInt(args[1]));
		}	
	}
	
	public void go(String IP, int port) {
		setUpNetworking(IP, port);
	
		// Instantiate a new ShutdownThread instance and invoke the addShutdownHook method to deal with ctrl+c on client console.
        fShutdownThread = new ShutdownThread(this);
        Runtime.getRuntime().addShutdownHook(fShutdownThread);
		
		// construct the GUI for user input		
		JFrame frame = new JFrame("Simple Chat Client");
		JPanel mainPanel = new JPanel();
		incoming = new JTextArea(15, 65);
		incoming.setLineWrap(true);
		incoming.setWrapStyleWord(true);
		incoming.setEditable(false);			
		JScrollPane qScroller = new JScrollPane(incoming);
		qScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		qScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);		
		outgoing = new JTextField(30);
		SendingCommand sendListen = new SendingCommand();
		outgoing.addActionListener(sendListen);		
		mainPanel.add(qScroller);
		mainPanel.add(outgoing);
		
		// start a new "reader" thread here to handle the incoming message from server
		Thread readerThread = new Thread(new IncomingReader());
		readerThread.start();
			
		frame.getContentPane().add(BorderLayout.CENTER, mainPanel);
		frame.setSize(800,500);
		frame.setVisible(true);
	} 
	
	// the shutdown routine when the user input ctrl+c in the ternimal
	// it automatically close all this client socket and remind the server do the necessary moves
	public void shutdown() {
		try	{
			writer.println("logout " + userName);
			writer.flush();					
			System.out.println("The client is exiting!");
		} catch (Exception e) { /* failed */ }
	}
	
	public void setUpNetworking(String IP, int port) {
		try {
			sock = new Socket(IP, port);	
			writer = new PrintWriter(sock.getOutputStream());			
			InputStreamReader streamReader = new InputStreamReader(sock.getInputStream());
			reader = new BufferedReader(streamReader);
			
			System.out.println("network established"); 
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}	
	
	// an inner class to implement the "outgoing" JTextField, which is in charge of sending user input to server
	public class SendingCommand implements ActionListener {
		public void actionPerformed(ActionEvent ev) {
			try {				
				String message = outgoing.getText();
				String[] parseCommand = message.split(" ");
				System.out.println("loginFlag: " + loginFlag);
				if(!loginFlag) {
					// authentication
					if(parseCommand.length <= 1) {
						writer.println(message);	
						writer.flush();
					}					
					else {
						System.out.println("1");
						writer.println("invalid username or password!");	
						writer.flush();
					}	
				}
				else {
					// list connected users
					if("whoelse".equals(parseCommand[0])) {
						System.out.println("list connected users");						
						writer.println(message + " " + userName);
						writer.flush();
					}
					// list all the users logged in last hour
					else if("wholasthr".equals(parseCommand[0])) {
						System.out.println("list users connected within last hour");						
						writer.println(message + " " + userName);
						writer.flush();
					}
					// when the user is broadcasting
					else if("broadcast".equals(parseCommand[0])) {
						System.out.println("broadcast");						
						writer.println(message + " " + userName);
						writer.flush();
						System.out.println("broadcast over");
					}
					// when the user is sending private message
					else if("message".equals(parseCommand[0])) { 
						System.out.println("private");
						writer.println(message + " " + userName);
						writer.flush();
					}
					// when the user want to logout
					else if("logout".equals(parseCommand[0])) {
						System.out.println("logout");
						writer.println(message + " " + userName);
						writer.flush();
					}
					else {
						System.out.println("error command");
						writer.println("error command");
						writer.flush();
					}	
				}		
			} catch(Exception ex) {
			ex.printStackTrace();
			}
			outgoing.setText("");
			outgoing.requestFocus();
		}
	} // close SendingCommand inner class

	// the inner class that tells what the new thread should do, which is in charge of responding to the input from the server 
	public class IncomingReader implements Runnable {
		public void run() {
			String message;
			try {
				while((message = reader.readLine()) != null) {
					String[] messageArray = message.split(" ");
					for(String token:messageArray)
						System.out.println(token);
					// check if the user is just logged in
					if(!loginFlag && messageArray[1].equals("Welcome")) {					
						userName = messageArray[0];
						//checkTime = System.currentTimeMillis();
						System.out.println("logging " + message);
						loginFlag = true;
						incoming.setText("");
						incoming.append(message + "\n\n");						
					}
					// check if the user is logged out or shut down by server 
					else if(loginFlag && message.contains("logged out!")) {
						System.out.println(message);
						loginFlag = false;
						incoming.append(message + "\n");
						sock.close();
						System.exit(0);						
					}
					// all the other cases
					else {
						System.out.println("read " + message);
						incoming.append(message + "\n");
					}
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}	
		}
	}
}	