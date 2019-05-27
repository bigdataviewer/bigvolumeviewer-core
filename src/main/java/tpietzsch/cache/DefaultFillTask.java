package tpietzsch.cache;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static tpietzsch.cache.TextureCache.ContentState.COMPLETE;
import static tpietzsch.cache.TextureCache.ContentState.INCOMPLETE;

public class DefaultFillTask implements FillTask
{
	final ImageBlockKey< ? > key;

	final Predicate< UploadBuffer > fill;

	final BooleanSupplier containsData;

	/**
	 * {@code fill.test()} fills buffer and returns whether block data was
	 * complete
	 */
	public DefaultFillTask( final ImageBlockKey< ? > key, final Predicate< UploadBuffer > fill, final BooleanSupplier containsData )
	{
		this.key = key;
		this.fill = fill;
		this.containsData = containsData;
	}

	@Override
	public ImageBlockKey< ? > getKey()
	{
		return key;
	}

	@Override
	public boolean containsData()
	{
		return containsData.getAsBoolean();
	}

	@Override
	public void fill( final UploadBuffer buffer )
	{
		final boolean complete = fill.test( buffer );
		buffer.setContentState( complete ? COMPLETE : INCOMPLETE );
	}
}
