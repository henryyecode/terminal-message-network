
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;
 
public class Server {
    static int port;
    static int block_duration;
    static int timeout;
    Set<UserThread> userThreads = new HashSet<>();					// List of current userThreads
    HashMap<String, String> userBase = new HashMap<>();				// Dictionary of all possible Username and passwords
    HashMap<String, Boolean> activeUsers = new HashMap<>();			// Dictionary of usernames and whether they are logged in or not
    HashMap<String, LocalDateTime> blocked = new HashMap<>();		// Dictionary of usernames and time, used for lock-out
    HashMap<String, LocalDateTime> loginLog = new HashMap<>();		// Dictionary of usernames and time, used for time-out
    HashMap<String, ArrayList<String>> offlineMessages = new HashMap<>();	// Dictionary of username and arraylist of messages
    
    
    // Constructor for Server
    public Server(int port, int block_duration, int timeout) {
        this.port = port;
        this.block_duration = block_duration;
        this.timeout = timeout;
    }
    
    // main function
    public static void main(String[] args) throws FileNotFoundException {
        if (args.length < 1) {
            System.out.println("Syntax: java Server.java <server-port> <");
            System.exit(0);
        }
        // instantiate server
        Server server = setUpServer(args);
        // read in credentials.txt
        server.readCredentials();
        // run the server
        server.execute();
    }
    // function to set up server
    private static Server setUpServer(String[] args) {
    	port = Integer.parseInt(args[0]);
        block_duration = Integer.parseInt(args[1]);
        timeout = Integer.parseInt(args[2]);
        return new Server(port, block_duration, timeout);
	}
    
    // function to read in credentials.txt
	private void readCredentials() throws FileNotFoundException {
    	File file = new File("Credentials.txt");
		Scanner scanner = new Scanner(file);
		while (scanner.hasNextLine()){
			StringTokenizer sT = new StringTokenizer(scanner.nextLine());
			String userName = sT.nextToken();
			String passWord = sT.nextToken();
			userBase.put(userName, passWord);
			activeUsers.put(userName, false);
			offlineMessages.put(userName, new ArrayList<String>());
		}
		scanner.close();
	}
	
	// function to run server
	public void execute() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
     
                // use threads to read in from multiple clients concurrently
                UserThread newUser = new UserThread(socket, this);
                newUser.start();
 
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// UserThread class to handle multiple clients at once
class UserThread extends Thread {
	private String userName;
	private InputStream input;
	private OutputStream output;
	private BufferedReader reader;
    private Socket socket;
    private Server server;
    private PrintWriter writer;
    ArrayList<String> blockedUser = new ArrayList<String>();
 
    public UserThread(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }
 
    public void run() {
        try {
        	// set up connections
        	setUpStreams();
        	
        	// run the authentication
            if (!authenticate()) {
            	
            } else {
            	// if successful, log in client
                logInClient();
                
                //send offline messages
                offlineMessage();

                // variable for timeout
                LocalDateTime idle = LocalDateTime.now();
                String message;
                
                // read messages until "logout" is read
                while (!((message = reader.readLine()).equals("logout"))){
                	// logout if idle for too long
                	if ((idle.plusSeconds(server.timeout)).isBefore(LocalDateTime.now())) {
                		reader.close();
                		break;
                	}
                	// read in message into application protocol
                	applicationProtocol(message);
                	// reset idle timer
                	idle = LocalDateTime.now();
                
                }
            
            }
            
            // when logout is read, proceed to logOutClient 
            logOutClient();
        } catch (IOException e) {
        }
    }
	private void applicationProtocol(String message) {
		StringTokenizer st = new StringTokenizer(message);
		String command = st.nextToken();
		switch (command) {
		case "message":
			if (st.countTokens() < 2) {
				sendMessage("Error. Invalid Command");
			}
			else {
				String user = st.nextToken();
				if (!server.userBase.containsKey(user)) {
					sendMessage("Error. User doesnt exist");
				}
				else if (st.countTokens() < 1) {
					sendMessage("Error. Invalid Command");
				}
				else {
					String argument = st.nextToken();
					while(st.hasMoreElements()){
						argument = argument.concat(" "+ st.nextToken());
					}
					messageUser(user, argument);
				}
			}
			break;
		case "broadcast":
			if (st.countTokens() < 1) {
				sendMessage("Error. Invalid Command");
			}
			else {
				String argument1 = st.nextToken();
				while(st.hasMoreElements()){
					argument1 = argument1.concat(" "+ st.nextToken());
				}
				broadcast(argument1);
			}
			break;
		case "whoelse":
			whoelse();
			break;
		case "whoelsesince":
			if (st.countTokens() < 1) {
				sendMessage("Error. Invalid Command");
			}
			else {
				String argument2 = st.nextToken();
				whoelsesince(argument2);
			}
			break;
		case "block":
			if (st.countTokens() < 1) {
				sendMessage("Error. Invalid Command");
			}
			else {
				String blockuser = st.nextToken();
				block(blockuser);
			}
			break;
		case "unblock":
			if (st.countTokens() < 1) {
				sendMessage("Error. Invalid Command");
			}
			else {
				String unblockuser = st.nextToken();
				unblock(unblockuser);
			}
			break;
		default:
			sendMessage("Error. Invalid Command");
		}
	}
	private void logInClient() {
		for (Map.Entry<String, Boolean> aU : server.activeUsers.entrySet()) {
			if (aU.getKey().equals(userName)) {
				if(aU.getValue() != true) {
					aU.setValue(true);
				}
			}	
		}
		String message = userName + " logged in";
		server.userThreads.add(this);
		if (server.userThreads.size() > 1) {
			for (UserThread uT : server.userThreads) {
	        	if(!uT.userName.equals(userName)) {
	        		uT.sendMessage(message);
	        		
	        	}
	        }
		}
	}
	private void logOutClient() throws IOException {
		socket.close();
		server.userThreads.remove(this);
    	for (Map.Entry<String, Boolean> aU : server.activeUsers.entrySet()) {
			if (aU.getKey().equals(userName)) {
				if(aU.getValue() == true) {
					aU.setValue(false);
					server.loginLog.put(userName, LocalDateTime.now());
				}
			}
		}
        String message = userName + " logged out";
        for (UserThread uT : server.userThreads) {
        	uT.sendMessage(message);
        }
	}

	private void setUpStreams() throws IOException {
    	input = socket.getInputStream();
        reader = new BufferedReader(new InputStreamReader(input));
        output = socket.getOutputStream();
        writer = new PrintWriter(output, true);	
	}

	private boolean authenticate() throws IOException {
		HashMap<String, Integer> attempts = new HashMap<>();
		sendMessage("Username: ");
        userName = reader.readLine();
		while(true) {
			boolean flag = false;
			
	        sendMessage("Password: ");
	        String password = reader.readLine();

	        //check if user is timed out
	        if (server.blocked.containsKey(userName)) {
	        	//if timed out dont continue
	        	if (LocalDateTime.now().isBefore(server.blocked.get(userName))) {
	        		sendMessage("Your account is blocked due to multiple login failures. Please try again later");
	        		break;
	        	} else {
	        		// if timeout complete unblock them
	        		server.blocked.remove(userName);
	        	}
	        }
	        
	        //keep count of login attempts
	        if (attempts.containsKey(userName)) {
	        	attempts.computeIfPresent(userName, (k, v) -> v + 1);
	        } else {
	        	attempts.put(userName, 1);
	        }

	        //check if username and pw match
			for (Map.Entry<String, String> UB : server.userBase.entrySet()) {
				if (userName.equals(UB.getKey())) {
					if (password.equals(UB.getValue())) {
						flag = true;
						// if they do reset the login attempt counter
						attempts.remove(userName);
						// also reset the last login log
						server.loginLog.remove(userName);
						
						// check if user already logged in
						if(server.activeUsers.get(userName)) {
							sendMessage("user already logged in");
							return authenticate();
						} else {
							sendMessage("Welcome to the greatest messaging application ever!");
							return true;
						}
					}
				}
			}
			if (flag == false) { // if unsuccessful
				
				for (Map.Entry<String, Integer> attempt : attempts.entrySet()) {
					// if its their third attempt
					if (attempt.getValue() == 3) {
						//lockout for block_duration
						LocalDateTime d = LocalDateTime.now().plusSeconds(server.block_duration);
						server.blocked.put(userName, d);
						sendMessage("Invalid Password. Your account has been blocked. Please try again later");
						System.out.println(d);
						return false;
					} else {
						sendMessage("Invalid Password. Please try Again");
					}
				}
				
			}
		}
		return false;
	}
	private void messageUser(String user, String message1) {
        boolean flag = false;
		for (UserThread uT : server.userThreads) {
        	if (uT.userName.equals(user) && !(uT.blockedUser.contains(userName))) {
                uT.sendMessage(userName + ": " + message1);
                flag = true;
            }
        	if (uT.userName.equals(user) && (uT.blockedUser.contains(userName))) {
        		sendMessage("Your message could not be delivered as the recipient has blocked you");
        		flag = true;
            }
        }
		if (!flag) {
			server.offlineMessages.get(user).add(userName + ": " + message1);
		}
	}
	
	private void offlineMessage() {
		ArrayList<String> messages = server.offlineMessages.get(userName);
		if (!messages.isEmpty()) {
			for (String S: messages) {
				sendMessage(S);
			}
		}	
	}

	private void broadcast(String argument1) {
		boolean flag = false;
        for (UserThread uT : server.userThreads) {
            if (!uT.blockedUser.contains(userName)) {
            	if (!uT.userName.equals(userName)) {
            		uT.sendMessage(userName + ": " + argument1);
            		flag = true;
            	}  
            }
        }
        if (!flag) {
        	sendMessage("Your message could not be delivered to some recipients");
        }
	}
	
	public void whoelse() {
		for (Map.Entry<String, Boolean> user : server.activeUsers.entrySet()) {
			if (user.getValue() && !user.getKey().equals(userName)) {
				sendMessage(user.getKey());
			}
		}
	}

	public void whoelsesince(String argument) {
		for (Map.Entry<String, LocalDateTime> user : server.loginLog.entrySet()) {
			if ((user.getValue().plusSeconds(Integer.parseInt(argument))).isAfter(LocalDateTime.now())) {
				sendMessage(user.getKey());
			}
		}
		whoelse();		
	}
	
	public void block(String argument) {
		if (argument.equals(userName)) {
			sendMessage("Error. Cannot block self");
			return;
		}
		if (!blockedUser.contains(argument)) {
			blockedUser.add(argument);
			sendMessage(argument + " is blocked");
		}
		
	}
	
	public void unblock(String argument) {
		if (!blockedUser.contains(argument)) {
			sendMessage("Error. " + argument + " was not blocked");
		} else {
			blockedUser.remove(argument);
			sendMessage(argument + " is unblocked");
		}
	}

    void sendMessage(String message) {
    	writer.println(message);
    }
}