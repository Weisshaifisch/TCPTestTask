package main;

import java.io.IOException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import client.Client;

public class MyClient {
	
	public static void main(String[] args) throws NumberFormatException, UnknownHostException, IOException {
		
		Client client = new Client(args[0], Integer.parseInt(args[1]));
		int numberOfThreads = Integer.parseInt(args[2]);
		for (int i = 0; i < numberOfThreads; ++i) {
			new Thread(new Caller(client)).start();
		}
	}

	private static class Caller implements Runnable {
		
		private Logger logger = LoggerFactory.getLogger(Caller.class);
		private Client client;
		
		public Caller(Client c) {
			this.client = c;
		}

		@Override
		public void run() {
			try {
				this.client.remoteCall("service1", "sleep", new Object[] {new Long(1000)});
				logger.info("Current date is: " + this.client.remoteCall("service1", "getCurrentDate", new Object[]{}));
			} catch (Exception e) {
				logger.error(e.getMessage());
			}
		}
	}
}

