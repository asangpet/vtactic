package ak.vtactic.util;

import org.apache.commons.collections.MapIterator;

public class MapIter<K,V> implements MapIterator {
	MapIterator source;
	
	public MapIter(MapIterator source) {
		this.source = source;
	}
	
	@Override
	public boolean hasNext() {
		return source.hasNext();
	}

	@SuppressWarnings("unchecked")
	@Override
	public K next() {
		return (K)source.next();
	}

	@SuppressWarnings("unchecked")
	@Override
	public K getKey() {
		return (K)source.getKey();
	}

	@SuppressWarnings("unchecked")
	@Override
	public V getValue() {
		return (V)source.getValue();
	}

	@Override
	public void remove() {
		source.remove();
	}

	@SuppressWarnings("unchecked")
	@Override
	public V setValue(Object value) {
		return (V)source.setValue(value);
	}
}
