package tpietzsch.blockmath3;

import java.util.ArrayList;
import java.util.List;
import net.imglib2.realtransform.AffineTransform3D;

public class RaiLevels
{
	private List< RaiLevel > raiLevels;

	private final AffineTransform3D sourceTransform;

	private int timepoint;

	private int setup;

	public RaiLevels()
	{
		raiLevels = new ArrayList<>();
		sourceTransform = new AffineTransform3D();
	}

	public RaiLevels( final List< RaiLevel > raiLevels, final AffineTransform3D sourceTransform, final int timepoint, final int setup )
	{
		this.raiLevels = raiLevels;
		this.sourceTransform = sourceTransform;
		this.timepoint = timepoint;
		this.setup = setup;
	}

	public List< RaiLevel > getRaiLevels()
	{
		return raiLevels;
	}

	public AffineTransform3D getSourceTransform()
	{
		return sourceTransform;
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
		return new RaiLevels( raiLevels, sourceTransform, timepoint, setup );
	}
}
