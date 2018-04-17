package tpietzsch.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * A set of listeners of type {@code T}.
 *
 * @param <T>
 *            listener type
 */
public interface Listeners< T >
{
	/**
	 * Add a listener to this set.
	 *
	 * @param listener
	 *            the listener to add.
	 * @return {@code true} if the listener was added. {@code false} if it was
	 *         already present.
	 */
	boolean add( final T listener );

	/**
	 * Removes a listener from this set.
	 *
	 * @param listener
	 *            the listener to remove.
	 * @return {@code true} if the listener was successfully removed.
	 *         {@code false} if the listener was not present.
	 */
	boolean remove( final T listener );

	default boolean addAll( final Collection< ? extends T > listeners )
	{
		return listeners.stream().map( this::add ).reduce( Boolean::logicalOr ).get();
	}

	default boolean removeAll( final Collection< ? extends T > listeners )
	{
		return listeners.stream().map( this::remove ).reduce( Boolean::logicalOr ).get();
	}

	/**
	 * Implements {@link Listeners} using an {@link ArrayList}.
	 */
	class List< T > implements Listeners< T >
	{
		private final Consumer< T > onAdd;

		public List( final Consumer< T > onAdd )
		{
			this.onAdd = onAdd;
		}

		public List()
		{
			this( o -> {} );
		}

		public final ArrayList< T > list = new ArrayList<>();

		@Override
		public boolean add( final T listener )
		{
			if ( !list.contains( listener ) )
			{
				list.add( listener );
				onAdd.accept( listener );
				return true;
			}
			return false;
		}

		@Override
		public boolean remove( final T listener )
		{
			return list.remove( listener );
		}

		public ArrayList< T > listCopy()
		{
			return new ArrayList<>( list );
		}
	}

	/**
	 * Extends {@link Listeners.List}, making {@code add} and {@code remove}
	 * methods synchronized.
	 */
	class SynchronizedList< T > extends List< T >
	{
		public SynchronizedList( final Consumer< T > onAdd )
		{
			super( onAdd );
		}

		public SynchronizedList()
		{
			super();
		}

		@Override
		public synchronized boolean add( final T listener )
		{
			return super.add( listener );
		}

		@Override
		public synchronized boolean remove( final T listener )
		{
			return super.remove( listener );
		}

		@Override
		public synchronized ArrayList< T > listCopy()
		{
			return super.listCopy();
		}
	}
}
