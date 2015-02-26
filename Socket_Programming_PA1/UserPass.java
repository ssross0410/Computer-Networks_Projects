import java.io.*;
import java.net.*;
import java.util.*;

// this class is for client and server class reading username and password
public class UserPass {
	
	private ArrayList<String> username;
	private ArrayList<String> password;
	
	public UserPass() {
		
		File userAccount = new File("user_pass.txt");
		// read the user_pass.txt file
		try {
			BufferedReader userReader = new BufferedReader(new FileReader(userAccount));
			username = new ArrayList<String>();
			password = new ArrayList<String>();
			int i = 0;
			String line = null;
			while( (line = userReader.readLine()) != null) {
				String[] info = line.split(" ");
				username.add(info[0]);
				password.add(info[1]);
				i++;
			}
		} catch(Exception ex) {
				ex.printStackTrace();
		}					
	}

	public ArrayList<String> getUsernames() {
		return username;
	}
	
	public ArrayList<String> getPasswords() {
		return password;
	}
}