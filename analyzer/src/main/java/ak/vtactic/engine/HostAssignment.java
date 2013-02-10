package ak.vtactic.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import ak.vtactic.primitives.ComponentNode;

public class HostAssignment implements Iterable<Host> {
	Map<String, Host> hosts = new TreeMap<>(); // map from name to host
	Map<ComponentNode, Host> hostMap = new HashMap<>(); // map from component to host

	public void add(Host host) {
		hosts.put(host.getName(), host);
	}
	
	public Host getHostByName(String name) {
		return hosts.get(name);
	}
	
	public Host getCurrentAssignment(ComponentNode node) {
		return hostMap.get(node);
	}
	
	public void assign(Host host, ComponentNode component) {
		Host current = getCurrentAssignment(component);
		if (current != null) {
			current.remove(component);
		}
		
		host.add(component);
		hostMap.put(component, host);
	}
	
	public Set<ComponentNode> getContenders(ComponentNode node) {
		Host host = hostMap.get(node);
		if (host == null) {
			return Collections.emptySet();
		}
		Set<ComponentNode> contenders = new HashSet<ComponentNode>(host.getComponents());
		// remove self
		contenders.remove(node);
		return contenders;
	}
	
	public Set<ComponentNode> getTenants(Host host) {
		return host.getComponents();
	}
	
	@Override
	public Iterator<Host> iterator() {
		return hosts.values().iterator();
	}
	
	public Iterator<Host> randomIterator() {
		List<Host> staticHost = new ArrayList<Host>(hosts.values());
		List<Host> randomHost = new ArrayList<Host>(staticHost.size());
		int idx = 0;
		while (!staticHost.isEmpty()) {
			int target = (int)Math.round(Math.floor(Math.random()*staticHost.size()));
			randomHost.add(idx, staticHost.get(target));
			staticHost.set(target, staticHost.get(staticHost.size()-1));
			staticHost.remove(staticHost.size()-1);
		}
		return randomHost.iterator();
	}
	
	public List<Host> getHostList() {
		return new ArrayList<Host>(hosts.values());
	}
}
