package tpietzsch.blockmath3;

import java.util.ArrayList;

public class RaiLevels
{
	private ArrayList< RaiLevel > raiLevels;

	private int timepoint;

	private int setup;

	public RaiLevels()
	{
		raiLevels = new ArrayList<>();
	}

	public RaiLevels( final ArrayList< RaiLevel > raiLevels, final int timepoint, final int setup )
	{
		this.raiLevels = raiLevels;
		this.timepoint = timepoint;
		this.setup = setup;
	}

	public ArrayList< RaiLevel > getRaiLevels()
	{
		return raiLevels;
	}

	public int getTimepoint()
	{
		return timepoint;
	}

	public int getSetup()
	{
		return setup;
	}

	public void set( final RaiLevels o )
	{
		this.raiLevels = o.raiLevels;
		this.timepoint = o.timepoint;
		this.setup = o.setup;
	}

	public RaiLevels copy()
	{
		return new RaiLevels( raiLevels, timepoint, setup );
	}
}
