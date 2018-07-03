package tpietzsch.blockmath;

public class RequiredBlock
{
	private final int[] gridPos;

	private int bestLevel;

	public RequiredBlock( final int[] gridPos, final int bestLevel )
	{
		this.gridPos = gridPos;
		this.bestLevel = bestLevel;
	}

	public int[] getGridPos()
	{
		return gridPos;
	}

	public int getBestLevel()
	{
		return bestLevel;
	}

	public void setBestLevel( final int bestLevel )
	{
		this.bestLevel = bestLevel;
	}
}
