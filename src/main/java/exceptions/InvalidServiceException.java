package exceptions;

public class InvalidServiceException extends Exception {
	
	private static final long serialVersionUID = 3140496850936889330L;

	public InvalidServiceException(String message) {
		super(message);
	}
}
