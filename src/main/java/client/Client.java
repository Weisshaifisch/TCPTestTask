package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.SocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exchange.Request;
import exchange.Response;

public class Client implements RemoteCalling {
	
	private static final AtomicLong nextRequestId = new AtomicLong(0);
	
	private Logger logger;	
	private int port;
	private String host;
	private Socket socket = null;
	private ObjectOutputStream serverOutputStream = null;
	private ObjectInputStream serverInputStream = null;
	private final Lock requestLock = new ReentrantLock();
	private final Lock responseLock = new ReentrantLock();
	private Condition outOfTurn = responseLock.newCondition();
	private static final AtomicInteger threadCounter = new AtomicInteger(0);
	private static final AtomicInteger threadPositionInQueue = new AtomicInteger(0);
	
	private static final ThreadLocal<Integer> threadLocalPosition = 
			new ThreadLocal<Integer>() {
				@Override
				protected Integer initialValue() {
					return 0;
				}
	};

	public Client(String host, int port) throws UnknownHostException, IOException {
		this.port = port;
		this.host = host;
		this.logger = LoggerFactory.getLogger(Client.class);
		 
		logger.info(String.format("Connecting to %s on port %d...", this.host, this.port));
		this.socket = SocketFactory.getDefault().createSocket(InetAddress.getByName(host), port);
		serverOutputStream = new ObjectOutputStream(this.socket.getOutputStream());
		serverOutputStream.flush();
		serverInputStream = new ObjectInputStream(this.socket.getInputStream());
	}

	@Override
	public Object remoteCall(String serviceName, String methodName, Object[] params) throws Exception {
		
		Request request = new Request(nextRequestId.incrementAndGet(), serviceName, methodName, params);
		
		requestLock.lock();		
		try	{
			logger.info("Sending a request " + request.toString() + " to the server");
			serverOutputStream.writeObject(request);
			threadLocalPosition.set(threadCounter.getAndIncrement());
		} catch (IOException e) {
			logger.error("Couldn't send a request " + request.toString() + " to the server");
			logger.error(e.getMessage());
		} finally {
			requestLock.unlock();
		}

		
		Response response = null;
		responseLock.lock();
		try {
			while (threadPositionInQueue.get() != threadLocalPosition.get()) {
				outOfTurn.await();
			}
			response = (Response) serverInputStream.readObject();
		} catch (ClassNotFoundException | IOException | InterruptedException e) {
			logger.error("Coudn't get a response for the request " + request.toString() + "from the server");
			logger.error(e.getMessage());
		} finally {
			threadPositionInQueue.getAndIncrement();
			outOfTurn.signalAll();
			responseLock.unlock();
		}
		
		if (null != response) {
			logger.info("Got response " + response.toString() + " for request id " + request.getId());
			Object result = response.getResult(); 
			if (null != result) {
				if (result instanceof Exception) {
					throw new Exception(result.toString());
				}
			}
			return result;
		}  
		
		return null;
	}
	
	public synchronized void close() throws IOException {
		if (null != serverOutputStream) {
			serverOutputStream.close();
		}
		
		if (null != serverInputStream) {
			serverInputStream.close();
		}
		
		if (null != this.socket) {
			this.socket.close();
		}
	}
}
