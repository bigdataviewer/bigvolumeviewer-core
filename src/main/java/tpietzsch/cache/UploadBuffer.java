package tpietzsch.cache;

import java.nio.Buffer;
import tpietzsch.blocks.CopySubArrayImp;
import tpietzsch.cache.TextureCache.ContentState;

public interface UploadBuffer
{
	/**
	 * Stores data for uploading to texture tile.
	 *
	 * @return a direct buffer
	 */
	Buffer getBuffer();

	/**
	 * Starting offset for tile in buffer.
	 *
	 * @return offset in bytes
	 */
	int getOffset();

	/**
	 * Called by client to say which image block was stored.
	 */
	void setImageBlockKey( ImageBlockKey< ? > key );

	/**
	 * Called by client to say whether image block data was complete.
	 */
	void setContentState( ContentState state );

	/**
	 * Which image block was stored?
	 */
	ImageBlockKey< ? > getImageBlockKey();

	/**
	 * Was stored image block data complete?
	 */
	ContentState getContentState();
}
