package server;

import java.util.Date;

public class Service1 {
	public void sleep(Long millis) throws InterruptedException {
		Thread.sleep(millis);
	}
	
	public Date getCurrentDate() {
		return new Date();
	}
}
