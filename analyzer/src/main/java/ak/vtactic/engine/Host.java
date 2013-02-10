package ak.vtactic.engine;

import java.util.HashSet;
import java.util.Set;

import ak.vtactic.primitives.ComponentNode;

import com.google.common.base.Objects;

public class Host implements Comparable<Host> {
	Set<ComponentNode> components = new HashSet<ComponentNode>();
	String name;
	
	public Host(String name) {
		this.name = name;
	}
	
	public Host setName(String name) {
		this.name = name;
		return this;
	}
	
	public String getName() {
		return name;
	}
	
	public void add(ComponentNode component) {
		components.add(component);
	}
	
	public void remove(ComponentNode component) {
		components.remove(component);
	}
	
	public Set<ComponentNode> getComponents() {
		return components;
	}
	
	@Override
	public String toString() {
		return Objects.toStringHelper(this)
			.add("name", name)
			.add("comp", components)
			.toString();
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		return name.equals(((Host)obj).name);
	}
	
	@Override
	public int compareTo(Host o) {
		return name.compareTo(o.name);	}
}
