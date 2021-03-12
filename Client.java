
import java.net.*;
import java.util.Scanner;
import java.io.*;

public class Client {
	
    private String hostname;
    private int port;
	static DataOutputStream output;
	static DataInputStream input;
	static boolean flag;
	
	// Constructor for a client
    public Client(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }
    
    // main function
    public static void main(String[] args) {
        if (args.length < 2) {
        	return;
        }
        // use args to construct client
        Client client = setUpClient(args);
        
        // set up threads to read and write to server
        client.setUpStreams();
    }
    
    // function to construct client
    public static Client setUpClient(String[] args) {
    	String hostname = args[0];
        int port = Integer.parseInt(args[1]);
		return new Client(hostname, port);
	}
    public void setUpStreams() {
        try {
        	// create socket for client
            Socket socket = new Socket(hostname, port);
            
            // set up threads to concurrently read from and write to server
            new ReadThread(socket, this).start();
            new WriteThread(socket, this).start();
            if (flag) {
            	System.exit(0);
            }
 
        } catch (UnknownHostException e) {
        	System.out.println("Unknown Host");
        } catch (IOException e) {
            System.out.println("I/O Error");
        }
 
    }
}
// ReadThread class to Read messages from InputStream
class ReadThread extends Thread {
    private BufferedReader reader;
    private Socket socket;
    private Client client;
    private InputStream input;
 
    public ReadThread(Socket socket, Client client) throws IOException {
        this.socket = socket;
        this.client = client;
        // set up reader
        this.input = socket.getInputStream();
        reader = new BufferedReader(new InputStreamReader(input));
        
    }
 
    public void run() {
        while (true) {
            try {
            	String response;
            	if (!(response = reader.readLine()).equals("logout")) {
            		System.out.println(response);
            	}
            	else {
            		Client.flag = true;
            	}
         
            } catch (IOException ex) {
                System.exit(0);
            } catch (NullPointerException ex) {
            	System.exit(0);
            }
        }
    }
}

//ReadThread class to Read messages from InputStream
class WriteThread extends Thread {
    private PrintWriter writer;
    private Socket socket;
    private Client client;
 
    public WriteThread(Socket socket, Client client) throws IOException {
        this.socket = socket;
        this.client = client;
        OutputStream output = socket.getOutputStream();
        writer = new PrintWriter(output, true);
    }
 
    public void run() {
    	Scanner scanner = new Scanner(System.in);
        String message;
        do {
            message = scanner.nextLine();
            writer.println(message);
        } while (!message.equals("logout"));
 
        try {
            socket.close();
            scanner.close();
        } catch (IOException ex) {
        	System.exit(0);
        }
    }
}