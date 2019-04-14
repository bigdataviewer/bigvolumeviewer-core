package tpietzsch.multires;

import bdv.cache.CacheControl;

public interface Stacks
{
	default int timepointId( final int timepointIndex )
	{
		return timepointIndex;
	}

	default int setupId( final int setupIndex )
	{
		return setupIndex;
	}

	MultiResolutionStack3D< ? > getStack( final int timepointId, final int setupId, final boolean volatil );

	CacheControl getCacheControl();

	default int getNumTimepoints()
	{
		return 1;
	}
}
