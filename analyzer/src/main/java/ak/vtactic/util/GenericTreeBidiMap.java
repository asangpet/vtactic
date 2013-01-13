package ak.vtactic.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.bidimap.TreeBidiMap;

public class GenericTreeBidiMap<K,V> implements OrderedBiMap<K,V> {
	TreeBidiMap sourceMap;
	
	public GenericTreeBidiMap() {
		sourceMap = new TreeBidiMap();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public V put(K key, V value) {
		return (V)sourceMap.put(key,value);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		return (V) sourceMap.get(key);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object key) {
		return (V)sourceMap.remove(key);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public K removeValue(V key) {
		return (K)sourceMap.removeValue(key);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public K firstKey() {
		return (K)sourceMap.firstKey();
	}
	
	@Override
	public boolean isEmpty() {
		return sourceMap.isEmpty();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public Set<K> keySet() {
		return sourceMap.keySet();
	}

	@Override
	public int size() {
		return sourceMap.size();
	}

	@Override
	public boolean containsKey(Object key) {
		return sourceMap.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return sourceMap.containsValue(value);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		sourceMap.putAll(m);
	}

	@Override
	public void clear() {
		sourceMap.clear();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<V> values() {
		return sourceMap.values();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return sourceMap.entrySet();
	}
	
	public MapIter<K,V> mapIterator() {
		return new MapIter<K,V>(sourceMap.mapIterator());
	}
}
