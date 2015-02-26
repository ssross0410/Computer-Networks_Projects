import java.io.*;
import java.net.*;
import java.util.*;

public class SimpleChatServer implements ShutdownThreadInterface{	
	
	// a shutdown hook to shut down all the sockets, to let the server and clients exit gracefully
	private  ShutdownThread fShutdownThread;
	
	private ArrayList<String> username;
	private ArrayList<String> password;
	
	// list to store the PrintWriter for broadcasting to all the client socket
	private ArrayList clientBroadcastStreams;	
	// list to store the PrintWriter for shutting down all the client socket
	private ArrayList shutdownWriters;
	
	// Hashmap to record currently connected users: username -> socket
	private Map<String, Socket> connectedUser; 
	
	// Hashmap to record users already entered username and entering password right now: socket -> username
	private Map<Socket,String> usernameEntered;
	
	// Hashmap to record number of failed attempts on password connected users: IP -> times
	private Map<String, Integer> failedTimes;
	
	// List to hold the users who have received offline messages
	private List<String> ifOfflineMessages;
	
	// List to store the offline message for different users
	private List<offlineMsg> offlineMessages;
	
	// Hashmap to record the latest logged in time for each user: username -> time
	private Map<String, Long> lastLoggedIn;
	
	// Hashmap to record the latest command issued time for each user: username -> time
	private Map<String, Long> lastCommand;
	
	// Hashmap to record the username and IP address pair being blocked: IP -> time
	private Map<String, Long> blockedUsers;
	
	public int TIME_OUT = 1800; // unit in seconds
	public int LAST_HOUR = 3600; // unit in seconds
	public int BLOCK_TIME = 60; // unit in seconds
	
	// a UserPass instance to read the lists of usernames and passwords
	private UserPass user_pass;
	
	ServerSocket serverSock;		// the server socket, which keeps listening the client socket
	
	// the client handler inner class for server to handle each client
	ClientHandler clientRunnable;	
	
	// the server constructor
	public SimpleChatServer() {
	
		// Instantiate a new ShutdownThread instance and invoke the addShutdownHook method to deal with ctrl+c on server console.
        fShutdownThread = new ShutdownThread(this);
        Runtime.getRuntime().addShutdownHook(fShutdownThread);
	
		connectedUser = new HashMap<String, Socket>();
		usernameEntered = new HashMap<Socket,String>();
		failedTimes = new HashMap<String, Integer>();
		lastLoggedIn = new HashMap<String, Long>();
		ifOfflineMessages = new ArrayList<String>();
		offlineMessages = new ArrayList<offlineMsg>(); 
		lastCommand = new HashMap<String, Long>();
		blockedUsers = new HashMap<String, Long>();
		
		user_pass = new UserPass();
		
		clientBroadcastStreams = new ArrayList();
		shutdownWriters = new ArrayList();
	}
	
	public static void main(String[] args) {
		if (args.length != 1) { 
			System.out.println("Command line arguments are not in correct format!"); 
		}
		else {
			SimpleChatServer server = new SimpleChatServer();
			server.go(Integer.parseInt(args[0]));
		}	
	}
	
	public void go(int port) {	
		readUserInfo();
		try {
			serverSock = new ServerSocket(port);
			while(true) {
				Socket clientSocket = serverSock.accept();
				clientRunnable = new ClientHandler(clientSocket);
				Thread t = new Thread(clientRunnable);
				t.start();
				
				PrintWriter writer = new PrintWriter(clientSocket.getOutputStream());
				clientBroadcastStreams.add(writer);
				shutdownWriters.add(writer);
				System.out.println("got a connection");
			} 
		} catch(Exception ex) {
				ex.printStackTrace();
		}
	}
	
	// the shutdown routine when the user input ctrl+c in the ternimal
	// it automatically close all the client sockets and then the server socket
	public void shutdown() {
		Iterator it = clientBroadcastStreams.iterator();
		try	{
			while(it.hasNext()) {
				PrintWriter writer = (PrintWriter) it.next();				
				writer.println("Due to server shut down, you are logged out!");
				writer.flush();										
			}
			serverSock.close();
			System.out.println("The server is shut down!");
		} catch (IOException e) { /* failed */ }
	}
	
	// read the username and password
	private void readUserInfo() {
		username = user_pass.getUsernames();
		password = user_pass.getPasswords();
	}
	
	// a helper function to store the offline messages for different users
	public void setupOffline(String receiver, String message, boolean flag) {
		// if the client has not received a offline message yet
		if(!flag) {
			offlineMsg off = new offlineMsg(receiver);
			off.addOfflineMsg(message);
			offlineMessages.add(off);
		}
		// if the client did received a offline message since last logOut
		else {
			int i;
			for (i = 0; i < offlineMessages.size(); i++) {
				if(receiver.equals(offlineMessages.get(i).getReceiver()))
					break;				
			}
			offlineMessages.get(i).addOfflineMsg(message);
		}
	}
	
	public class ClientHandler implements Runnable {
		BufferedReader reader;
		Socket sock;
		PrintWriter writer;
		String ip;
		String name;

		public ClientHandler(Socket clientSocket) {
			try {
				sock = clientSocket;
				InputStreamReader isReader = new InputStreamReader(sock.getInputStream());
				reader = new BufferedReader(isReader);
				// get the client's IP address
				ip = sock.getRemoteSocketAddress().toString();
				failedTimes.put(ip,0);
				//off = new offlineMsg();
				
				writer = new PrintWriter(sock.getOutputStream());
				writer.println("Enter Username Below");
				writer.flush();
				
			} catch(Exception ex) {
				ex.printStackTrace();
			}
		}
		// @override run method in the Runnable interface
		public void run() {
			String message;
			try {
				while((message = reader.readLine()) != null) {
					System.out.println("read " + message);
					String[] messageArray = message.split(" ");
					String firstString = messageArray[0];
					if(firstString.equals("whoelse")) {
						// list currently logged in users
						if(messageArray.length == 2) 
							listConnected(message.substring(8));
						else {
							writer.println("Invalid command! Please enter again!");
							writer.flush();	
						}
					}		
					// list users connected in last one hour
					else if(firstString.equals("wholasthr")) {
						if(messageArray.length == 2) 
							listConnectedHR(message.substring(10));
						else {
							writer.println("Invalid command! Please enter again!");
							writer.flush();	
						}
					}
					// broadcast the user's message 
					else if(firstString.equals("broadcast")) {
						if(messageArray.length > 2) {
							System.out.println("here broadcast");
							broadcast(message.substring(10)); }
						else { 
							writer.println("Invalid command! Please enter again!");
							writer.flush();	
						}	
					}
					// send user's private message	
					else if(firstString.equals("message")) {
						if(messageArray.length > 3) {
							privateMessage(message.substring(8));
							System.out.println("here private"); }
						else {
							writer.println("Invalid command! Please enter again!");
							writer.flush();	
						}	
					}
					// log the user out 	
					else if(firstString.equals("logout")) {
						if(messageArray.length == 2) 
							logOut(message.substring(7));
						else {
							writer.println("Invalid command! Please enter again!");
							writer.flush();	
						}	
					}	
					// command is invalid
					else if(firstString.equals("error")) {
						writer.println("Invalid command! Please enter again!");
						writer.flush();
					}
					// when user entered invalid authentication info
					else if(firstString.equals("invalid")) {
						writer.println("Please Enter Valid Username or Password");
						writer.flush();
					}	
					// when user is still entering authentication info	
					else {
						authenticate(message);
						System.out.println("default");
					}
				}	
			
			} catch(Exception ex) {
				ex.printStackTrace();
			}	
		}
		
		private void authenticate(String message) {
			// encounter blocked IP and username combination
			if(blockedUsers.containsKey(ip+message) && (System.currentTimeMillis() - blockedUsers.get(ip+message)) <= BLOCK_TIME*1000) {
				writer.println("Sorry! Your IP address on user '" + message + "' is blocked!");
				writer.flush();
			}
			else {
				// the socket has not entered a username before
				if(!usernameEntered.containsKey(sock)) {	
					usernameEntered.put(sock,message);
					// valid username
					if(username.contains(message)) {
						// if the username is already logged in
						if(connectedUser.containsKey(message)) {
							usernameEntered.remove(sock);
							writer.println("Sorry! The username you are trying to logging is already logged in! Please enter another username!");
							writer.flush();
						}
						// prompt the user to enter password
						else {
							writer.println("Please enter password!");
							writer.flush();
						}
					}
					// invalid username
					else {
						usernameEntered.remove(sock);
						writer.println("Invalid username! Please enter username again!");
						writer.flush();
					}
				}	
				else {
					// the username has been entered and a valid password
					if(password.contains(message)) {
						name = usernameEntered.get(sock);
						int index = user_pass.getUsernames().indexOf(name);
						String pass = user_pass.getPasswords().get(index);
						// correct password
						if(message.equals(pass)) {
							connectedUser.put(name, sock);
							lastLoggedIn.put(name, System.currentTimeMillis());
							lastCommand.put(name,System.currentTimeMillis());
							failedTimes.remove(ip);
							if(ifOfflineMessages.contains(name)) {
								try	{
									BufferedWriter bufferWriter = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
									bufferWriter.write(name + " Welcome to Our Chat Room!");
									bufferWriter.newLine();
									int i, j;
									System.out.println("username: " + name);
									// obtain the offline messages for this user
									for (i = 0; i < offlineMessages.size(); i++) {
										if(name.equals(offlineMessages.get(i).getReceiver()))
											break;				
									}
									for(j = 0; j < offlineMessages.get(i).getOfflineMsg().size();j++) {
										bufferWriter.write(offlineMessages.get(i).getOfflineMsg().get(j));
										bufferWriter.newLine();	
									}
									bufferWriter.flush();
									// after logging in, remove the previous offline messages for this users
									ifOfflineMessages.remove(name);
									offlineMessages.remove(i);
								} catch(Exception ex) {
									ex.printStackTrace();
								}			
							}
							else {
								writer.println(name + " Welcome to Our Chat Room!");
								writer.flush();
							}	
						}
						// incorrect password
						else {
							failedTimes.put(ip, failedTimes.get(ip) + 1); // increment the times of failed attempts
							// failed attempts has not reached the limit
							if(failedTimes.get(ip) < 3) {
								writer.println("Wrong password! Please enter username and password again!");
								writer.flush();
							}
							// failed attempts has reached the limit
							else {
								name = usernameEntered.get(sock);
								blockedUsers.put(ip + name,System.currentTimeMillis());
								failedTimes.put(ip,0);
								writer.println("Sorry you are blocked on this IP and '" + name + "'due to too many failed attempts on password! You can come back in " + BLOCK_TIME + " seconds!");
								writer.flush();
							}
						}
						usernameEntered.remove(sock);
					}	
					// invalid password
					else {
						failedTimes.put(ip, failedTimes.get(ip) + 1); // increment the times of failed attempts
						// failed attempts has not reached the limit
						if(failedTimes.get(ip) < 3) {
							writer.println("Wrong password! Please enter username again!");
							writer.flush();
						}
						// failed attempts has reached the limit
						else {
							name = usernameEntered.get(sock);
							blockedUsers.put(ip + name,System.currentTimeMillis());
							failedTimes.put(ip,0);
							writer.println("Sorry! You are blocked on this IP and '" + name + "' due to too many failed attempts on password! You can try again in " + BLOCK_TIME/60 + " minutes!");
							writer.flush();
						}
						usernameEntered.remove(sock);
					}
				}	
			}
		}
		
		private void listConnected(String username) {
			try	{				
				BufferedWriter bufferWriter = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
				if(System.currentTimeMillis() - lastCommand.get(username) < TIME_OUT*1000) {
					lastCommand.put(username,System.currentTimeMillis());
					boolean empty = true;
					for(Map.Entry<String, Socket> entry : connectedUser.entrySet()) {
						if(!username.equals(entry.getKey())) {
							empty = false;
							bufferWriter.write(entry.getKey() + "   [whoelse]");
							bufferWriter.newLine();
						}
					}
					if(!empty)
						bufferWriter.flush();
					else {
						writer.println("No other currently logged in users!");
						writer.flush();
					}					
				}	
				else {
					writer.println("Sorry Due to too long inactivation time! You have already been automatically logged out!");
					writer.flush();
					autoLogOut(username);
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}	
		}
		
		private void listConnectedHR(String username) {
			try	{
				BufferedWriter bufferWriter = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
				if(System.currentTimeMillis() - lastCommand.get(username) < TIME_OUT*1000) {
					lastCommand.put(username,System.currentTimeMillis());
					boolean empty = true;
					for(Map.Entry<String, Long> entry : lastLoggedIn.entrySet()) {
						long loggedInTime = System.currentTimeMillis() - entry.getValue(); 
						if(!username.equals(entry.getKey()) && loggedInTime < LAST_HOUR*1000) {
							empty = false;
							bufferWriter.write(entry.getKey() + "   [wholasthr]");
							bufferWriter.newLine();
						}
					}
					if(!empty) 
						bufferWriter.flush();
					else {
						writer.println("No other users logged in within one hour!");
						writer.flush();
					}
				}
				else {
					writer.println("Sorry! Due to too long inactivation time! You have already been automatically logged out!");
					writer.flush();
					autoLogOut(username);
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}				
		}
		
		private void broadcast(String message) {
			String[] messageArray = message.split(" ");
			String broadcaster = messageArray[messageArray.length - 1];
			try	{
				if(System.currentTimeMillis() - lastCommand.get(broadcaster) < TIME_OUT*1000) {
					lastCommand.put(broadcaster,System.currentTimeMillis());
					Iterator it = clientBroadcastStreams.iterator();
					message = message.substring(0,(message.length() - broadcaster.length() - 1));
					while(it.hasNext()) {
						PrintWriter writer = (PrintWriter) it.next();
						writer.println(broadcaster + ": " + message + "   [broadcast]");
						writer.flush();
					}		
				} 
				else {
					writer.println("Sorry! Due to too long inactivation time! You have already been automatically logged out!");
					writer.flush();
					autoLogOut(broadcaster);
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}						
		}
		
		private void privateMessage	(String message) {
			String[] messageArray = message.split(" ");
			String sender = messageArray[messageArray.length - 1];
			String receiver = messageArray[0];
			
			message = message.substring((receiver.length() + 1), (message.length() - sender.length() - 1)); 
			try	{
				if(System.currentTimeMillis() - lastCommand.get(sender) < TIME_OUT*1000) {
					lastCommand.put(sender,System.currentTimeMillis());
					if(connectedUser.containsKey(receiver)) {	
						Socket receiverSock = connectedUser.get(receiver);
						PrintWriter writerPrivate = new PrintWriter(receiverSock.getOutputStream());
						writerPrivate.println(sender + ": " + message + "   [message]");
						writerPrivate.flush();						
					}
					else if(username.contains(receiver)){
						String offline = sender + ": " + message + "   [offline message]";
						// if no offline message yet, add the username to the offline message users list
						if(!ifOfflineMessages.contains(receiver)) {
							ifOfflineMessages.add(receiver);
							setupOffline(receiver,offline,false);
						}
						// if already has offline message, just add the message into the list
						else {
							setupOffline(receiver,offline,true);
						}
					}
				}
				else {
					writer.println("Sorry! Due to too long inactivation time! You have already been automatically logged out!");
					writer.flush();
					autoLogOut(sender);
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}
		}
		
		private void logOut(String username) {
			Iterator<Map.Entry<String, Socket>> iterator = connectedUser.entrySet().iterator();
			try	{
				if(System.currentTimeMillis() - lastCommand.get(username) < TIME_OUT*1000) {
					while(iterator.hasNext()){
						Map.Entry<String, Socket> entry = iterator.next();
						if(username.equals(entry.getKey())) {
							iterator.remove();
							lastCommand.remove(username);
							ifOfflineMessages.remove(username);
							writer.println(username + " You are successfully logged out!");
							writer.flush();
							sock.close();
							break;
						}	
					}
				} 	
				else {
					writer.println("Sorry! Due to too long inactivation time! You have already been automatically logged out!");
					writer.flush();
					autoLogOut(username);
				}	
			} catch(Exception ex) {
				ex.printStackTrace();
			}	
		}	
		
		private void autoLogOut(String username) {
			Iterator<Map.Entry<String, Socket>> iterator = connectedUser.entrySet().iterator();
			try	{
				while(iterator.hasNext()){
					Map.Entry<String, Socket> entry = iterator.next();
					if(username.equals(entry.getKey())) {
						iterator.remove();
						lastCommand.remove(username);
						sock.close();
						break;
					}	
				}
			} catch(Exception ex) {
					ex.printStackTrace();
			}
		}	
	} 	
}