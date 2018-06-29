package tpietzsch.cache;

import java.nio.Buffer;

import tpietzsch.blocks.ByteUtils;
import tpietzsch.cache.TextureCache.ContentState;

public class UploadBuffer implements ByteUtils.Address
{
	private final Buffer buffer;
	private final int offset;
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
	 * Called by FillTask to say whether stored image block data was complete.
	 */
	public void setContentState( final ContentState state )
	{
		this.state = state;
	}

	/**
	 * Was stored image block data complete?
	 */
	public ContentState getContentState()
	{
		return state;
	}

	/**
	 * ...tentative...
	 */
	@Override
	public long getAddress()
	{
		return ByteUtils.addressOf( buffer ) + offset;
	}
}
