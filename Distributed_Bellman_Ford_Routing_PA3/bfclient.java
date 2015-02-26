import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class bfclient {	
	
	// GUI components
	JTextArea incoming;
	JTextField outgoing;
	
	private int TIMEOUT;				// timeout
	private int localPort;				// local port
	String extIP;
	private boolean CLOSE = false;
	
	// the list of the alive neighbors of the client: IP:Port
	private ArrayList<String> aliveNeighbors;

	// the hashtable of the client's DV list:  IP:Port -> Cost
	private Map<String, Integer> distanceVector;

	// the hashtable of the client's first hop clients: Destination(IP:Port) -> FirstHop(IP:Port)
	private Map<String, String> firstHopNodes;

	// the hashtable recording the last time hearing from a neighbor:  IP:Port -> Time
	private Map<String, Long> lastHearFromNeighbor;

	// the hashtable of the cost of the direct link to each neighbor when stablized: 
	// neighbor's (IP:Port) -> direct link cost
	private Map<String, Integer> directLinks;

	// the hashtable of the linkdown-ed neighbors: IP:Port -> Cost
	private Map<String, Integer> linkDownLinks;

	// the hashtable of distance vectors of each neighbor: neighbor's IP:Port -> its DV
	private Map<String, NeighborInfo> neighborInfo;

	// the list of the nodes being closed
	public static ArrayList<String> closedNode;

	// the writing and listening UDP socket
	private DatagramSocket writingSocket, listeningSocket;

	// the timer to schedule different timer tasks
	java.util.Timer timer;
	

	public bfclient() {
		try {
			extIP = InetAddress.getLocalHost().getHostAddress();
			System.out.println(extIP);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		// initialize all the hashmaps and arraylists
		aliveNeighbors = new ArrayList<String>();
		distanceVector = new HashMap<String, Integer>();
		firstHopNodes = new HashMap<String, String>();
		lastHearFromNeighbor = new HashMap<String, Long>();
		directLinks = new HashMap<String, Integer>();
		linkDownLinks = new HashMap<String, Integer>();
		neighborInfo = new HashMap<String, NeighborInfo>();
		closedNode = new ArrayList<String>();
		timer = new java.util.Timer(true);
	}

	// the main program
	public static void main(String[] args) {
		if (args.length < 5 || args.length % 3 != 2) { 
			System.out.println("Command line arguments are not in correct format!"); 
		}
		else {
			bfclient client = new bfclient();	 
			client.go(args);
		}	
	}
	
	public void go(String[] args) {
		
		// parse the input arguments
		localPort = Integer.parseInt(args[0]);
		TIMEOUT = Integer.parseInt(args[1]);
		System.out.println("TIMEOUT: " + TIMEOUT);

		// initialize the neighboring information
		for(int i = 2;i < args.length;i = i+3) {
			String IP = args[i];
			String port = args[i+1];
			String cost = args[i+2];
			String aliveNeighbor = IP + ":" + port;
			aliveNeighbors.add(aliveNeighbor);
			int costVal = Integer.parseInt(cost);
			distanceVector.put(aliveNeighbor, costVal);
			firstHopNodes.put(aliveNeighbor, aliveNeighbor);
			directLinks.put(aliveNeighbor, costVal);
			lastHearFromNeighbor.put(aliveNeighbor, System.currentTimeMillis());
			NeighborInfo neighborDV = new NeighborInfo(aliveNeighbor);
			neighborInfo.put(aliveNeighbor, neighborDV);
		}
		
		// construct the GUI for user input		
		JFrame frame = new JFrame("Client Node");
		JPanel mainPanel = new JPanel();
		incoming = new JTextArea(15, 65);
		incoming.setLineWrap(true);
		incoming.setWrapStyleWord(true);
		incoming.setEditable(false);
		JScrollPane qScroller = new JScrollPane(incoming);
		qScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		qScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);		
		outgoing = new JTextField(20);
		
		Command sendListen = new Command();
		outgoing.addActionListener(sendListen);		
		mainPanel.add(qScroller);
		mainPanel.add(outgoing);
	
		frame.getContentPane().add(BorderLayout.CENTER, mainPanel);
		frame.setSize(800,500);
		frame.setVisible(true);
		try {

			// start a new listening thread
			Thread listenThread = new Thread(new ListeningDV(timer));
			//listenThread.setDaemon(true);
			listenThread.start();

			// start a new sending thread
			Thread sendThread = new Thread(new SendingDV(timer));
			//sendThread.setDaemon(true);
			sendThread.start();
	
		} catch(Exception ex) {	
			ex.printStackTrace();
		} 
	}

	// the inner class that tells what the new thread should do, which is in charge of responding to the input from the server 
	public class ListeningDV implements Runnable {
		java.util.Timer timer;

		public ListeningDV(java.util.Timer timer) throws SocketException {
			try {
				listeningSocket = new DatagramSocket(localPort);
				this.timer = timer;
			} catch(Exception ex) {
				ex.printStackTrace();
			}	
		}

		public void run() {
			try {
				timer.scheduleAtFixedRate(
					new TimerTask() {
						public void run() {
            				checkNeiborAlive(CLOSE);
            				checkClosedNode(CLOSE);
            				checkDV(CLOSE);
       					}
    				}, 0, 3*TIMEOUT*1000); // run every 3*TIMEOUT seconds
				
				while(true) {
					byte[] receivedStream = new byte[1024];
					DatagramPacket receivedPacket = new DatagramPacket(receivedStream, receivedStream.length);
					listeningSocket.receive(receivedPacket);
					
					if(!CLOSE) {
						String receivedMsg = new String(receivedPacket.getData());
						receivedMsg = receivedMsg.trim();
						String[] splitMsg = receivedMsg.split(" ");
						
						// ******* Receive CLOSE message  ******* //
						if(splitMsg[0].equals("close")) {
							receiveCloseMsg(splitMsg[1], splitMsg[2]);
						}
						
						// ******* Receive LINKDOWN message  ******* //
						else if(splitMsg[0].equals("linkdown")) {
							receiveLinkDown(splitMsg[1]);
						}
						
						// ******* Receive LINKUP message  ******* //
						else if(splitMsg[0].equals("linkup")) {
							receiveLinkUp(splitMsg[1]);
						}

						// ******* Receive ROUTE UPDATE message  ******* //
						else {
							String heardNeighbor = splitMsg[0];
							sendClose(false, heardNeighbor);
							if(closedNode.contains(heardNeighbor)) {
								closedNode.remove(heardNeighbor);
								
							}
							updateHeardNeighbor(splitMsg, heardNeighbor);
							boolean changeDV = updateDV(splitMsg, heardNeighbor);
							if(changeDV) {
								sendDV(CLOSE);
							}
						}	
					}	
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}	
		}

		// check any dead neighbor
		private void checkNeiborAlive(boolean flag) {
			try {
				if(!flag) {
					Iterator<Map.Entry<String, Long>> iterator = lastHearFromNeighbor.entrySet().iterator();
					while(iterator.hasNext()) {
		   				Map.Entry<String, Long> entry = iterator.next();
		   				if(System.currentTimeMillis() - entry.getValue() > 3 * TIMEOUT * 1000) {
		   					String node = entry.getKey();
			   				iterator.remove();
			   				aliveNeighbors.remove(node);
			   				distanceVector.remove(node);
			   				firstHopNodes.remove(node);
			   				directLinks.remove(node);
			   				neighborInfo.remove(node);
			  				sendClose(true, node);
			   				if(!closedNode.contains(node)) {
			   					closedNode.add(node);
			   				}	
		   				}
		   			}
		   		}	
	   		} catch(Exception ex) {
				ex.printStackTrace();
			}			
		}
		
		// check any closed node
		private void checkClosedNode(boolean flag) {
			if(!flag) {
				for(int i = 0;i < closedNode.size();i++) {
					String deadNode = closedNode.get(i);
					if(distanceVector.containsKey(deadNode)) {
						distanceVector.remove(deadNode);
						firstHopNodes.remove(deadNode);
					}

					if(neighborInfo.containsKey(deadNode))
						neighborInfo.remove(deadNode);

					if(firstHopNodes.containsValue(deadNode)) {
						Iterator<Map.Entry<String, String>> iterator = firstHopNodes.entrySet().iterator();
						while(iterator.hasNext()) {
		   					Map.Entry<String, String> entry = iterator.next();
		   					String dest = entry.getKey();
		   					String link = entry.getValue();
		   					if(link.equals(deadNode)) {
		   						if(!directLinks.containsKey(dest)) {
			   						distanceVector.remove(dest);
			   						iterator.remove();
			   					}
			   					else {
			   						int dist = directLinks.get(dest);
			   						distanceVector.put(dest, dist);
			   						entry.setValue(dest);
			   					}	
		   					}
		   				}
					}
				}
			}	
		}

		// check and update the DV according to the DV of the client's neighbor
		private void checkDV(boolean flag) {
			if(!flag) {
				Iterator<Map.Entry<String, Integer>> iterator = distanceVector.entrySet().iterator();
				while(iterator.hasNext()) {
		   			Map.Entry<String, Integer> entry = iterator.next();
		   			String dest = entry.getKey();
		   			int cost = entry.getValue();
		   			String link = firstHopNodes.get(dest);
		   			int selfToLinkCost = distanceVector.get(link);
		   			int calculatedLinkToDestCost = cost - selfToLinkCost;
		   			if(neighborInfo.containsKey(link)) {
			   			NeighborInfo neighborDV = neighborInfo.get(link);
			   			int linkToDestCost = neighborDV.getLinkToDestCost(dest);
			   			if(calculatedLinkToDestCost < linkToDestCost) {
			   				entry.setValue(selfToLinkCost + linkToDestCost);
			   			}
			   		}		
		   		}
			}	
		}

		private void sendClose(boolean isOn, String node) {
			try {
				if(isOn) {
					String wrappedClose = "close " + "on " + node;
					byte[] sendStream = wrappedClose.getBytes();
					for(int i = 0; i < aliveNeighbors.size();i++) {
						String entry = aliveNeighbors.get(i);
						String[] neighbor = entry.split(":");
						String IP = neighbor[0];
						String port = neighbor[1];
						DatagramPacket sendPacket = new DatagramPacket(sendStream,sendStream.length,
							InetAddress.getByName(IP),Integer.parseInt(port));
						writingSocket.send(sendPacket);
					}
				}
				else {
					String wrappedClose = "close " + "off " + node;
					byte[] sendStream = wrappedClose.getBytes();
					for(int i = 0; i < aliveNeighbors.size();i++) {
						String entry = aliveNeighbors.get(i);
						String[] neighbor = entry.split(":");
						String IP = neighbor[0];
						String port = neighbor[1];
						DatagramPacket sendPacket = new DatagramPacket(sendStream,sendStream.length,
							InetAddress.getByName(IP),Integer.parseInt(port));
						writingSocket.send(sendPacket);
					}	
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			} 			
		}
		
		private void updateHeardNeighbor(String[] splitDV, String sourceDV) {
			try {
				// if the source of the received distance vector is not in the client's 
				// neighbors list,  then add the new or restored neighbor node to the 
				// corrsponding lists and tables
				lastHearFromNeighbor.put(sourceDV, System.currentTimeMillis());
				if(!aliveNeighbors.contains(sourceDV)) {
					aliveNeighbors.add(sourceDV);			
					for(int i = 1;i < splitDV.length;i++) {
						String[] splitNeighbor = splitDV[i].split(":");
						if(splitNeighbor.length == 5) {
							String destination = splitNeighbor[0] + ":" + splitNeighbor[1];
							int cost = Integer.parseInt(splitNeighbor[2]);
							NeighborInfo neighborDV = new NeighborInfo(sourceDV);
							neighborInfo.put(sourceDV, neighborDV);
							// when the destination is the client itself
							if(destination.equals(extIP + ":" + Integer.toString(localPort))) {
								distanceVector.put(sourceDV, cost);
								firstHopNodes.put(sourceDV, sourceDV);
								directLinks.put(sourceDV, cost);
								neighborDV = neighborInfo.get(sourceDV);
								neighborDV.updateNeighborDV(extIP + ":" + Integer.toString(localPort), cost);
								neighborInfo.put(sourceDV, neighborDV);
								break;
							}
						}	
					}	
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}			
		}

		private boolean updateDV(String[] splitDV, String sourceDV) {
			boolean changeDV = false;
			try {
				ArrayList<String> checker = new ArrayList<String>();
				NeighborInfo neighborDV = new NeighborInfo(sourceDV);
	
				for(int i = 1;i < splitDV.length;i++) {
					String[] splitNeighbor = splitDV[i].split(":");
					if(splitNeighbor.length == 5) {
						String destination = splitNeighbor[0] + ":" + splitNeighbor[1];
						int cost = Integer.parseInt(splitNeighbor[2]);
						checker.add(destination);
						// update the distance vector of this neighbor(the source of this received DV)
						neighborDV.updateNeighborDV(destination, cost);
		
							// when the destination node is not the client itself
							if(!destination.equals(extIP + ":" + Integer.toString(localPort))) {
								
								// when the destination node is not in the client's DV list
								if(!distanceVector.containsKey(destination)) {
									if(!closedNode.contains(destination) || linkDownLinks.containsKey(destination)) {
										int updatedCost = distanceVector.get(sourceDV) + cost;
										distanceVector.put(destination, updatedCost);
										firstHopNodes.put(destination, firstHopNodes.get(sourceDV));	
										changeDV = true;
									}	
								}
								// when the destination node is already in the client's DV list
								else {
									int updatedCost = distanceVector.get(sourceDV) + cost;
									if(updatedCost < distanceVector.get(destination)) {
										if(!closedNode.contains(destination) || linkDownLinks.containsKey(destination)) {
											distanceVector.put(destination, updatedCost);
											firstHopNodes.put(destination, firstHopNodes.get(sourceDV));
											changeDV = true;
										}	
									}
								}	
							}
							// when the destination is client ifself, then just add directly the corresponding info
							// into the distance vector and link vector
							else {
								distanceVector.put(destination, 0);
								firstHopNodes.put(destination, destination);
							}
						}	
					}	
				neighborInfo.put(sourceDV, neighborDV);

				if(!distanceVector.isEmpty()) {
					Iterator<Map.Entry<String, Integer>> iterator = distanceVector.entrySet().iterator();
					while(iterator.hasNext()) {
		   				Map.Entry<String, Integer> entry = iterator.next();
		   				String dest = entry.getKey();
		   				if(!checker.contains(dest) && firstHopNodes.get(dest).equals(sourceDV) 
		   					&& !dest.equals(sourceDV)) {
		   					iterator.remove();
		   					firstHopNodes.remove(dest);
		   					changeDV = true;
		   				}
		   			}
		   		}
			} catch(Exception ex) {
				ex.printStackTrace();
			} finally {
				return changeDV;	
			}	
		}

		private void receiveCloseMsg(String isOn, String nodeInfo) {
			if(isOn.equals("on")) {
				if(!closedNode.contains(nodeInfo)) {
					closedNode.add(nodeInfo);
				}
			}
			else {
				if(closedNode.contains(nodeInfo)) {
					closedNode.remove(nodeInfo);
				}
			}
		}
		
		private void receiveLinkDown(String sourceInfo) {
			if(!linkDownLinks.containsKey(sourceInfo)) 
				linkDownLinks.put(sourceInfo, directLinks.get(sourceInfo));
		}

		private void receiveLinkUp(String sourceInfo) {
			if(linkDownLinks.containsKey(sourceInfo))
				linkDownLinks.remove(sourceInfo);
		}
	}	

	public class SendingDV implements Runnable {
		java.util.Timer timer;

		public SendingDV(java.util.Timer timer) {
			try {
				writingSocket = new DatagramSocket();
				this.timer = timer;
			} catch(Exception ex) {
				ex.printStackTrace();
			}
		}

		public void run() {
			try {
				timer.scheduleAtFixedRate(
					new TimerTask() {
						public void run() {
	            			sendDV(CLOSE);
       					}
    				}, 0, TIMEOUT*1000); // run every TIMEOUT seconds
			} catch(Exception ex) {
				ex.printStackTrace();
			}	
		}
	}
	
	private synchronized void sendDV(boolean flag) {
		try {
			if(!flag) {
				String wrappedDVList = wrapSendingDV();
				byte[] sendStream = wrappedDVList.getBytes();
				for(int i = 0; i < aliveNeighbors.size();i++) {
					String entry = aliveNeighbors.get(i);
					String[] neighbor = entry.split(":");
					String IP = neighbor[0];
					String port = neighbor[1];
					DatagramPacket sendPacket = new DatagramPacket(sendStream,sendStream.length,
						InetAddress.getByName(IP),Integer.parseInt(port));
					writingSocket.send(sendPacket);
				}	
			}	
		} catch(Exception ex) {
			ex.printStackTrace();
		}	
	}

	private synchronized String wrapSendingDV() {
		String wrappedDVList = new String();
		wrappedDVList = extIP + ":" + Integer.toString(localPort) + " ";
		Iterator<Map.Entry<String, Integer>> iterator = distanceVector.entrySet().iterator();
		while(iterator.hasNext()) {
	   		Map.Entry<String, Integer> entry = iterator.next();
	   		if(!entry.getKey().equals(extIP + ":" + Integer.toString(localPort))) {
		   		String dest = entry.getKey();
		   		String cost = Integer.toString(entry.getValue());
		   		String link = firstHopNodes.get(dest);
		  		wrappedDVList += dest + ":" + cost + ":" + link + " ";
		  	}	
	   	}
		return wrappedDVList;
	}


	private void sendLinkDown(String ip, String port) {
		try {
			String wrappedLinkDown = "linkdown " + extIP + ":" + Integer.toString(localPort);
			byte[] sendStream = wrappedLinkDown.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendStream,sendStream.length,
				InetAddress.getByName(ip),Integer.parseInt(port));
			writingSocket.send(sendPacket);
		} catch(Exception ex) {
			ex.printStackTrace();
		}	
	}

	private void sendLinkUp(String ip, String port) {
		try {
			String wrappedLinkDown = "linkup " + extIP + ":" + Integer.toString(localPort);
			byte[] sendStream = wrappedLinkDown.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendStream,sendStream.length,
				InetAddress.getByName(ip),Integer.parseInt(port));
			writingSocket.send(sendPacket);
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	private void linkDownCommand(String targetInfo) {	
		try {
			timer.schedule(
				new TimerTask() {
					public void run() {
						linkDownLinks.put(targetInfo, directLinks.get(targetInfo));
						aliveNeighbors.remove(targetInfo);
						directLinks.remove(targetInfo);
						distanceVector.clear();
						firstHopNodes.clear();
						lastHearFromNeighbor.clear();
						neighborInfo.clear();

						Iterator<Map.Entry<String, Integer>> iterator = directLinks.entrySet().iterator();
						while(iterator.hasNext()) {
		   					Map.Entry<String, Integer> entry = iterator.next();
		   					String neigh = entry.getKey();
		   					int cost = entry.getValue();
		   					distanceVector.put(neigh, cost);
		   					firstHopNodes.put(neigh, neigh);
		   					lastHearFromNeighbor.put(neigh, System.currentTimeMillis());
		   					NeighborInfo neighborDV = new NeighborInfo(neigh);
							neighborInfo.put(neigh, neighborDV);
						}
						CLOSE = false;
						sendDV(CLOSE);
	       			}
    			}, 6*TIMEOUT*1000);
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	private void linkUpCommand(String targetInfo) {
		try {
			int cost = linkDownLinks.get(targetInfo);
			aliveNeighbors.add(targetInfo);
			directLinks.put(targetInfo, cost);
			distanceVector.put(targetInfo, cost);
			firstHopNodes.put(targetInfo, targetInfo);
			lastHearFromNeighbor.put(targetInfo, System.currentTimeMillis());
			NeighborInfo neighborDV = new NeighborInfo(targetInfo);
			neighborDV.updateNeighborDV(extIP + ":" + Integer.toString(localPort), cost);
			neighborInfo.put(targetInfo, neighborDV);
			linkDownLinks.remove(targetInfo);
			sendDV(CLOSE);
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	public class Command implements ActionListener {
		public void actionPerformed(ActionEvent ev) {
			try {				
				String message = outgoing.getText();
				if(!message.contains(" ")) {

					// ******* Command: SHOWRT  ******* //

					if(message.equals("SHOWRT")) {
						incoming.append("Current Time: " + String.format("%tc", new Date()) + "   " + 
							"Distance vector list is:" + "\n");
						if(distanceVector.isEmpty()) {
							incoming.append("Empty distance vector!" + "\n");
						}
						else {
							int i = 0;
							Iterator<Map.Entry<String, Integer>> iterator = distanceVector.entrySet().iterator();
							while(iterator.hasNext()) {
		   						Map.Entry<String, Integer> entry = iterator.next();
		   						String dest = entry.getKey();
		   						String cost = Integer.toString(entry.getValue());
		   						String link = firstHopNodes.get(dest);
		   						String tuple = "Destination = " + dest + ", Cost = " + cost + ", Link = (" + link + ")";
		   						incoming.append(tuple + "\n");
		   					}	
						}	
					}

					// ******* Command: CLOSE  ******* //

					else if(message.equals("CLOSE")) {
						System.exit(0);
					}
					else {
						incoming.append("Invalid Command!" + "\n");
					}
				}
				else {
					String[] command = message.split(" ");

					// ******* Command: LINKDOWN  ******* //

					if(command[0].equals("LINKDOWN")) {
						String targetInfo = command[1] + ":" + command[2];
						if(aliveNeighbors.contains(targetInfo)) {
							CLOSE = true;
							incoming.append("Link Down with: " + targetInfo + "\n");
							incoming.append("It will take around 5 to 6 * TIMEOUT for all the clients to converge. Please wait patiently! " + "\n");
							sendLinkDown(command[1], command[2]);
							linkDownCommand(targetInfo);	
						}
						else {
							incoming.append("Invalid Link!" + "\n");
						}
					}

					// ******* Command: LINKUP  ******* //

					else if(command[0].equals("LINKUP")) {
						String targetInfo = command[1] + ":" + command[2];
						if(linkDownLinks.containsKey(targetInfo)) {
							sendLinkUp(command[1], command[2]);
							linkUpCommand(targetInfo);
						}
						else {
							incoming.append("Invalid Link!" + "\n");
						}
					}
					else {
						incoming.append("Invalid Command!" + "\n");
					}
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}
			outgoing.setText("");
			outgoing.requestFocus();
		}
	} // close Command inner class
}	