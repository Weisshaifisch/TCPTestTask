package exchange;

import java.io.Serializable;

public class Response implements Serializable {

	private static final long serialVersionUID = 6477286660882240904L;
	
	private Long id;
	private Object result;

	public Response(Long id, Object result) {
		this.id = id;
		this.result = result;
	}

	public Long getId() {
		return id;
	}

	public void setResult(Object result) {
		this.result = result;
	}

	public Object getResult() {
		return result;
	}

	@Override
	public String toString() {
		return String.format("<id: %d - %s>" , id, (result == null) ? "void" : result.toString());
	}

}
