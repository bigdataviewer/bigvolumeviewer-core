package bvv.util;

import net.imglib2.type.numeric.ARGBType;

public abstract class BvvSource implements Bvv
{
	private BvvHandle bvv;

	// so that we can fix the bdv numTimepoints when removing sources.
	private final int numTimepoints;

	protected BvvSource( final BvvHandle bvv, final int numTimepoints )
	{
		this.bvv = bvv;
		this.numTimepoints = numTimepoints;
	}

	// invalidates this BvvSource completely
	// closes bdv if it was the last source
	public abstract void removeFromBdv();

	public abstract void setDisplayRange( final double min, final double max );

	public abstract void setDisplayRangeBounds( final double min, final double max );

	public abstract void setColor( final ARGBType color );

	public abstract void setCurrent();

	public abstract boolean isCurrent();

	public abstract void setActive( final boolean isActive );

	@Override
	public BvvHandle getBvvHandle()
	{
		return bvv;
	}

	protected void setBdvHandle( final BvvHandle bdv )
	{
		this.bvv = bdv;
	}

	protected abstract boolean isPlaceHolderSource();

	protected int getNumTimepoints()
	{
		return numTimepoints;
	}
}
