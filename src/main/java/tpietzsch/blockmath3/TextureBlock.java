package tpietzsch.blockmath3;

public class TextureBlock
{
	/**
	 * Get the grid coordinates of the block.
	 *
	 * @return grid coordinates of the block.
	 */
	public int[] getGridPos()
	{
		return gridPos;
	}

	/**
	 * Get the min texture coordinates of the block.
	 *
	 * @return min texture coordinates of the block.
	 */
	public int[] getPos()
	{
		return pos;
	}

	private final int[] gridPos; // TODO: REMOVE?

	private final int[] pos;

	private boolean needsLoading;

	public TextureBlock( final int[] gridPos, final int[] pos )
	{
		this.gridPos = gridPos;
		this.pos = pos;
	}

	public boolean needsLoading()
	{
		return needsLoading;
	}

	public void setNeedsLoading( final boolean needsLoading )
	{
		this.needsLoading = needsLoading;
	}
}
