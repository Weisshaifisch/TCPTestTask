package client;

@FunctionalInterface
public interface RemoteCalling {
	Object remoteCall(String serviceName, String methodName, Object[] params) throws Exception;
}
