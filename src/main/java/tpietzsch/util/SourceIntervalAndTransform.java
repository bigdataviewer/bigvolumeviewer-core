package tpietzsch.util;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;

public class SourceIntervalAndTransform
{
	private Interval sourceInterval;

	private final AffineTransform3D sourceTransform;

	public SourceIntervalAndTransform()
	{
		sourceInterval = null;
		sourceTransform = new AffineTransform3D();
	}

	public SourceIntervalAndTransform( final Interval i, final AffineTransform3D t )
	{
		sourceInterval = i == null ? null : new FinalInterval( i );
		sourceTransform = new AffineTransform3D();
		sourceTransform.set( t );
	}

	public void set( SourceIntervalAndTransform other )
	{
		this.sourceInterval = new FinalInterval( other.sourceInterval );
		this.sourceTransform.set( other.sourceTransform );
	}

	public SourceIntervalAndTransform copy()
	{
		return new SourceIntervalAndTransform( sourceInterval, sourceTransform );
	}

	public Interval getSourceInterval()
	{
		return sourceInterval;
	}

	public AffineTransform3D getSourceTransform()
	{
		return sourceTransform;
	}
}
