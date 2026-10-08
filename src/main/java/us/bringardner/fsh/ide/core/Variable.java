package us.bringardner.fsh.ide.core;

/** A variable and its value, as the debugger shows it. */
public class Variable {
	private final String name;
	private final Object value;

	public Variable(String name, Object value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public Object getValue() {
		return value;
	}

	@Override
	public String toString() {
		return name;
	}
}
