package exchange;

import java.io.Serializable;

public class Request implements Serializable {	
	
	private static final long serialVersionUID = 5420567442522691619L;

	private Long id;
	private String serviceName;
	private String methodName;
	private Object[] params;
	
	public Request(Long id, String serviceName, String methodName, Object[] params) {
		this.id = id;
		this.serviceName = serviceName;
		this.methodName = methodName;
		this.params = params;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("<id: %d - %s.%s(", id, serviceName, methodName));
		for (Object param : params) {
			sb.append(param.getClass().getSimpleName())
			.append(" ")
			.append(param);
		}
		sb.append(");>");
		return sb.toString();
	}

	public Long getId() {
		return id;
	}

	public String getServiceName() {
		return serviceName;
	}

	public String getMethodName() {
		return methodName;
	}

	public Object[] getParams() {
		return params;
	}
}
