package ak.vtactic.analyzer;

import java.util.Iterator;
import java.util.NoSuchElementException;

import ak.vcon.model.ResponseInfo;

public class MarkerFilterIterable<T extends ResponseInfo> implements Iterable<T> {
	final Iterable<T> source;
	
	public MarkerFilterIterable(Iterable<T> source) {
		 this.source = source;
	}
	
	@Override
	public Iterator<T> iterator() {
		final Iterator<T> iterator = source.iterator();
		return new Iterator<T>() {
			T next = null;
			
			@Override
			public T next() {
				if (next == null) {
					throw new NoSuchElementException();
				}
				T result = next;
				next = null;
				return result;
			}
			
			@Override
			public void remove() {
				return;
			}

			@Override
			public boolean hasNext() {
				boolean result = iterator.hasNext();
				if (result) {
					next = iterator.next();
					if (next.getRequest().startsWith("/marker")) {
						result = false;
					}
				}
				return result;
			}
		};
	}
};
