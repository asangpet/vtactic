package ak.vtactic.util;

import java.util.Map;

public interface OrderedBiMap<K,V> extends Map<K,V> {
	K removeValue(V key);
	K firstKey();
	MapIter<K,V> mapIterator();
}
