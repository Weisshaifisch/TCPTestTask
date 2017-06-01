package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.net.ServerSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exceptions.InvalidServiceException;
import exchange.Request;
import exchange.Response;

public class MyServer {

	private static Logger logger = LoggerFactory.getLogger(MyServer.class);
	//private static ExecutorService threadPool = Executors.newFixedThreadPool(10);
	private static ExecutorService threadPool = Executors.newCachedThreadPool();
	
	private int port;
	private ServerSocket srvSocket = null;
	private boolean isStopped = false;
	private Map<String, Object> services;
	private BlockingQueue<Request> requestsQueue;
	private BlockingQueue<Response> responsesQueue;
	
	private final Lock responseLock = new ReentrantLock();
	private Condition validResponse;
		
	public MyServer(int port) {
		this.port = port;		
		this.services = new ConcurrentHashMap<>();
		Properties props = new Properties();
		requestsQueue = new LinkedBlockingQueue<>();
		responsesQueue = new LinkedBlockingQueue<>();
		validResponse = responseLock.newCondition();
		
		try {
			props.load(getClass().getResourceAsStream("/server.properties"));
			Enumeration<?> e = props.propertyNames();
			while (e.hasMoreElements()) {
				String serviceName = (String)e.nextElement();
				try {
					Class<?> serviceClass = Class.forName(props.getProperty(serviceName));
					Object service = serviceClass.newInstance();
					if (null != service) {
						services.put(serviceName, service);
					}
				} catch (ClassNotFoundException ex) {
					logger.error("A service with name " + serviceName + " is not registered");
				} catch (InstantiationException | IllegalAccessException ex) {
					logger.error("Unable to instantiate a service class .\n" + ex.getMessage());
				}
			}
		}
		catch (IOException ioe) {
			logger.error("Can't load properties from the \"server.properties\" file");
		} 
	}
	
	private void acceptClients() {
		while (!(Thread.currentThread().isInterrupted() || isStopped())) {
			logger.info("Waiting for clients...");
			try {
				final Socket socket = srvSocket.accept();
				logger.info("Accepted connection from " + socket.getInetAddress().getHostName() + " on port " + socket.getPort());
				new Thread(new ClientThread(socket)).start();
			}
			catch (IOException e) {
				logger.error("Unable to accept a connection");
				if (isStopped()) {
					logger.error("The server is stopped");
					return;
				}
			}
		}
	}
	
	public void runServer() throws InterruptedException {
		logger.info("Starting server...");
		try {
			this.srvSocket = ServerSocketFactory.getDefault().createServerSocket(this.port);
		} catch (IOException e) {
			logger.error("Can't open the port " + port);
		}
		
		logger.info("Server is listening on port " + port);
		Thread t = new Thread(() -> {
			acceptClients();		
		});
		
		t.start();
		t.join();
	}
	
	private synchronized boolean isStopped() {
		return this.isStopped;
	}
	
	public synchronized void stop() {
		threadPool.shutdown();
		this.isStopped = true;		
		try {
			this.srvSocket.close();
		} catch (IOException e) {
			logger.error("Unable to close a server socket");
		}
	}
	
	private class ClientThread implements Runnable {
		
		private Socket socket;
		private ObjectOutputStream clientOutputStream = null;
		private ObjectInputStream clientInputStream = null;
		
		private final ThreadLocal<Boolean> isSocketClosed = 
				new ThreadLocal<Boolean>() {
					@Override
					protected Boolean initialValue() {
						return false;
					}
				};
		
		private final ThreadLocal<Boolean> isStreamAvailable = 
				new ThreadLocal<Boolean>() {
					@Override
					protected Boolean initialValue() {
						return true;
					}
				};
		
		public ClientThread(Socket socket) throws IOException {
			this.socket = socket;
		}
		
		@Override
		public void run() {			
			try {
				clientOutputStream = new ObjectOutputStream(this.socket.getOutputStream());
				clientOutputStream.flush();
				ObjectInputStream clientInputStream = new ObjectInputStream(this.socket.getInputStream());
				
				while(!(Thread.currentThread().isInterrupted() || isStopped() || isSocketClosed.get()) && isStreamAvailable.get()) {

					try {
						Request request = (Request) clientInputStream.readObject();
						logger.info("Received a request from the client: " + request.toString());
						requestsQueue.put(request);
						responsesQueue.put(new Response(request.getId(), null));
//						Future<?> task = threadPool.submit(new RequestProcessor(clientOutputStream));
//						logger.info("Task is done: " + task.isDone());
						threadPool.submit(new RequestProcessor(clientOutputStream));
					} catch (IOException | ClassNotFoundException e) {
						if (this.socket.isClosed()) {
							isSocketClosed.set(true);
							logger.info("A client has disconnected");
						}
						else if (clientInputStream.available() == 0) {
							isStreamAvailable.set(false);
							logger.info("A client's input stream is not available");
						}
						else {
							logger.error("Couldn't get a request from the client");
							e.printStackTrace();
							logger.error(e.getMessage());
						}
					} catch (InterruptedException e) {
						logger.error(e.getMessage());
					}
				}
			} catch (IOException e) {
				logger.error("An error has occured while obtaining one of the client streams");
				logger.error(e.getMessage());
			} finally {
				try {
					this.socket.close();
				} catch (IOException e) {
					logger.error("Couldn't close client connection");
					logger.error(e.getMessage());
				}
				
				if (null != clientInputStream) {
					try {					
						clientInputStream.close();
					} catch (IOException e) {
						logger.error("Couldn't close client's input stream");
						logger.error(e.getMessage());
					}
				}
				
				if (null != clientOutputStream) {
					try {
						clientOutputStream.close();
					} catch (IOException e) {
						logger.error("Couldn't close client's output stream");
						logger.error(e.getMessage());
					}
				}
			}
		}
	}
	
	private class RequestProcessor implements Runnable {
		
		private final ObjectOutputStream clientOutputStream;
		private final ThreadLocal<Request> request = 
				new ThreadLocal<Request>() {
					@Override
					protected Request initialValue() {
						return null;
					}
		};
		
		public RequestProcessor(ObjectOutputStream clientOutputStream) {
			this.clientOutputStream = clientOutputStream;
		}
		
		private void sendResponse(Object result) {						
			responseLock.lock();
			Response response = null;
			try {
				while (responsesQueue.peek().getId().compareTo(request.get().getId()) != 0) {
					validResponse.await();
				}
				
				response = responsesQueue.take();
				response.setResult(result);				
				logger.info("Sending a response " + response.toString() + " back to the client");
				clientOutputStream.writeObject(response);
			} catch (IOException | InterruptedException e) {
				logger.error("Unable to send response with id " + response.toString() + " back to the client");
			} finally {				
				validResponse.signalAll();
				responseLock.unlock();
			}
		}
		
		private Object getService() throws InvalidServiceException {
			Object service = services.get(request.get().getServiceName());
			if (null == service) {
				throw new InvalidServiceException(String.format("Unknown service '%s'", request.get().getServiceName()));
			}
			
			return service;
		}
		
		private Method getServiceMethod(Object service) throws NoSuchMethodException, SecurityException {
			List<Class<?>> paramTypes = Arrays.stream(request.get().getParams()).map(o -> o.getClass()).collect(Collectors.toList());
			Method method = service.getClass().getDeclaredMethod(request.get().getMethodName(), paramTypes.toArray(new Class[paramTypes.size()]));
			return method;
		}
		
		@Override
		public void run() {
			
			Object result = null;
			try {
				request.set(requestsQueue.take());
				Object service = getService();
				Method serviceMethod = getServiceMethod(service);				
				result = serviceMethod.invoke(service, request.get().getParams());
			} catch (InterruptedException | SecurityException | IllegalAccessException | InvocationTargetException e) {
				logger.error("Couldn't process a request " + request.toString());
				logger.error(e.getMessage());
				result = e;
			} catch (NoSuchMethodException e) {
				logger.error("Unknown service method: " + request.get().getMethodName());
				logger.error(e.getMessage());
				result = e;
			} catch (IllegalArgumentException e) {
				result = e;
			} catch (InvalidServiceException e) {
				logger.error("Unknown service requested: " + request.get().getServiceName());
				result = e;
			} finally {
				sendResponse(result);
			}
		}
	}

	public static void main(String[] args) {
		
		if (args.length < 1) {
			logger.info("Usage: MyServer <port_number>");
			System.exit(1);
		}
		
		try {
			new MyServer(Integer.parseInt(args[0])).runServer();
		} 
		catch (NumberFormatException e) {
			logger.error("Unable to read a port to listen to\n");
			logger.info("Usage: MyServer <port_number>");
		}
		catch (InterruptedException e) {
			logger.info("A server process has been interrupted.");
		}
	}
}
