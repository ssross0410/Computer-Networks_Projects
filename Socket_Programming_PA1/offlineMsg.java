import java.io.*;
import java.net.*;
import java.util.*;

public class offlineMsg {	
	private ArrayList<String> message;
	private String receiver;
	
	public offlineMsg(String s) {
		message = new ArrayList<String>();
		receiver = s;
	}
	
	public void addOfflineMsg(String s) {
		message.add(s);
	}
	
	public String getReceiver() {
		return receiver;
	}
	
	public ArrayList<String> getOfflineMsg() {
		return message;
	}

}