package tpietzsch.cache;

import java.nio.Buffer;
import tpietzsch.cache.TextureCache.ContentState;

public class UploadBuffer
{
	private final Buffer buffer;
	private final int offset;
	private ImageBlockKey< ? > key;
	private ContentState state;

	public UploadBuffer( final Buffer buffer, final int offset )
	{
		this.buffer = buffer;
		this.offset = offset;
	}

	/**
	 * Stores data for uploading to texture tile.
	 *
	 * @return a direct buffer
	 */
	public Buffer getBuffer()
	{
		return buffer;
	}

	/**
	 * Starting offset for tile in buffer.
	 *
	 * @return offset in bytes
	 */
	public int getOffset()
	{
		return offset;
	}

	/**
	 * Called by client to say which image block was stored.
	 */
	public void setImageBlockKey( final ImageBlockKey< ? > key )
	{
		this.key = key;
	}

	/**
	 * Called by client to say whether image block data was complete.
	 */
	public void setContentState( final ContentState state )
	{
		this.state = state;
	}

	/**
	 * Which image block was stored?
	 */
	public ImageBlockKey< ? > getImageBlockKey()
	{
		return key;
	}

	/**
	 * Was stored image block data complete?
	 */
	public ContentState getContentState()
	{
		return state;
	}
}
