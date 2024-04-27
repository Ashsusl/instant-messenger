package client;

import shared.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.*;

public class Client {
	private String host;
	private int port;
	private Socket socket;
	private Scanner scanner;
	public ObjectInputStream read;
	public ObjectOutputStream write;

	private final ClientUser user = new ClientUser();
	public static GUI gui;

	public Client() {
		this.host = "127.0.0.1";
		this.port = 3000;

		scanner = new Scanner(System.in);
	}

	public void doMessageReadLoop()
			throws IOException, ClassNotFoundException, ExecutionException, InterruptedException {
		int attempts = 3;
		boolean success = false;
		while (!success && attempts > 0) {
			System.out.println("Username: ");
			String user = scanner.nextLine();

			System.out.println("Password: ");
			String pass = scanner.nextLine();

			success = login(user, pass);
			--attempts;
		}

		if (success) {
			OutQueue outQueue = new OutQueue(write);
			Thread outThread = new Thread(outQueue);
			outThread.start();

			InQueue inQueue = new InQueue(read);
			Thread inThread = new Thread(inQueue);
			inThread.start();

			System.out.println("Successfully logged in.");

			boolean quit = false;
			do {
				System.out.println("Enter a message, or type 'logout' to quit: ");
				String in = scanner.nextLine();
				if (in.equals("logout")) {
					System.out.println("Logging out...");

					if (logout()) {
						System.out.println("(Client) Successfully logged out.");
					} else {
						System.out.println("Error logging out.");
					}
					quit = true;
					System.exit(0);
				} else {
					sendMessage(new Message(user.getUserId(), null, Message.Type.TEXT, Message.Status.REQUEST, in,
							user.getConversationId())); // Pass conversationId
				}
			} while (!quit);
		}
	}

	public void connectToServer() throws IOException {
		try {
			this.socket = new Socket(this.host, this.port);
			this.write = new ObjectOutputStream(socket.getOutputStream());
			this.read = new ObjectInputStream(socket.getInputStream());

		} catch (IOException e) {
			System.err.println("Error in I/O operations: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public boolean login(String username, String password) throws IOException, ClassNotFoundException {
		Message m = new Message(0, null, Message.Type.LOGIN, Message.Status.REQUEST,
				String.format("username: %s password: %s", username, password), -1); // also here we will treat -1 as
																						// the server recipient.

		write.writeObject(m);
		Message res = (Message) read.readObject();
		if (res.getType() == Message.Type.LOGIN && res.getStatus() == Message.Status.SUCCESS) {
			user.setUserId(res.getSenderId());
			user.setConversationId(res.getConversationId());
			return true;
		} else {
			return false;
		}
	}

	public boolean logout() throws IOException, ClassNotFoundException {
		Message m = new Message(user.getUserId(), null, Message.Type.LOGOUT, Message.Status.REQUEST, "Logging out!",
				-1); // We will be treating -1 as message intended for the server.

		write.writeObject(m);
		write.flush();
		Message res = (Message) read.readObject();
		if (res.getType() == Message.Type.LOGOUT && res.getStatus() == Message.Status.SUCCESS) {
			System.out.println("(Client) Successfully logged out.");
			return true;
		} else {
			System.out.println("Error logging out.");
			return false;
		}
	}

	public void viewConversation(int conversationID) {

	}

	public void sendMessage(Message message) throws IOException, ClassNotFoundException {
		OutQueue.out.add(message);
	}

	public void receiveMessages() {

	}

	public static class InQueue implements Runnable {
		// public static Queue<Message> in = new ConcurrentLinkedQueue<>();
		private final ObjectInputStream read;
		private volatile boolean quit = false;

		public InQueue(ObjectInputStream read) throws IOException {
			this.read = read;
		}

		@Override
		public void run() {
			try {
				while (!quit) {
					Message message = (Message) read.readObject();
					if (message != null) {
						// in.add(message);
						System.out.println(message.getContent());
					}
				}
			} catch (IOException | ClassNotFoundException e) {
				if (!quit) {
					System.out.println("Error in InQueue: " + e.getMessage());
				}
			}
		}

		public void handleMessages() {

		}

		public void quit() {
			quit = true;
			Thread.currentThread().interrupt();
			try {
				if (read != null) {
					read.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static class OutQueue implements Runnable {
		public static BlockingQueue<Message> out = new LinkedBlockingQueue<>();
		private final ObjectOutputStream write;
		private volatile boolean quit = false;

		public OutQueue(ObjectOutputStream write) throws IOException {
			this.write = write;
		}

		public void run() {
			while (!quit) {
				try {
					Message message = out.poll();
					if (message != null) {
						write.writeObject(message);
						write.flush();
						System.out.println("Sent message: " + message.getContent());
					} else {
						try {
							Thread.sleep(100); // Need to snooze for a bit, just in case...
						} catch (InterruptedException e) {
							quit = true;
						}
					}
				} catch (IOException e) {
					if (!quit) {
						System.out.println("Error in OutQueue: " + e.getMessage());
						quit = true;
					}
				}
			}
		}

		public void quit() {
			quit = true;
			Thread.currentThread().interrupt();
			try {
				write.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		System.out.println("Hello from the client!");

		Client client = new Client();

		try {
			client.connectToServer();
		} catch (IOException e) {
			System.out.println("Error connecting to server.");
			System.exit(1);
		}

		try {
			client.doMessageReadLoop();
		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Error in message loop.");
			System.exit(1);

		} catch (ExecutionException | InterruptedException e) {
			System.out.println("Error in auth.");
			System.exit(1);
		}
	}
}
