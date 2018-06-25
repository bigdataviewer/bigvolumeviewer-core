package tpietzsch.cache;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static tpietzsch.cache.TextureCache.ContentState.COMPLETE;
import static tpietzsch.cache.TextureCache.ContentState.INCOMPLETE;

public class UploadSet
{

	static class Entry implements FillTask
	{
		final ImageBlockKey< ? > key;

		final Predicate< UploadBuffer > fill;

		/**
		 * {@code fill.test()} fills buffer and returns whether block data was
		 * complete
		 */
		public Entry( final ImageBlockKey< ? > key, final Predicate< UploadBuffer > fill )
		{
			this.key = key;
			this.fill = fill;
		}

		@Override
		public ImageBlockKey< ? > getKey()
		{
			return key;
		}

		@Override
		public void fill( final UploadBuffer buffer )
		{
			final boolean complete = fill.test( buffer );
			buffer.setImageBlockKey( key );
			buffer.setContentState( complete ? COMPLETE : INCOMPLETE );
		}
	}

	public Set< Entry > entries = new HashSet<>();
}
