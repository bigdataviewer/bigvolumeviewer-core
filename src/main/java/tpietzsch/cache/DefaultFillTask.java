package tpietzsch.cache;

import java.util.function.Predicate;

import static tpietzsch.cache.TextureCache.ContentState.COMPLETE;
import static tpietzsch.cache.TextureCache.ContentState.INCOMPLETE;

class DefaultFillTask implements FillTask
{
	final ImageBlockKey< ? > key;

	final Predicate< UploadBuffer > fill;

	/**
	 * {@code fill.test()} fills buffer and returns whether block data was
	 * complete
	 */
	public DefaultFillTask( final ImageBlockKey< ? > key, final Predicate< UploadBuffer > fill )
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
		buffer.setContentState( complete ? COMPLETE : INCOMPLETE );
	}
}
