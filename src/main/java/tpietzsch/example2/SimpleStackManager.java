package tpietzsch.example2;

import tpietzsch.backend.GpuContext;
import tpietzsch.multires.SimpleStack3D;

public interface SimpleStackManager
{
	SimpleVolume getSimpleVolume( GpuContext context, SimpleStack3D< ? > stack );

	/**
	 * Free allocated resources associated to all stacks that have not been
	 * {@link #getSimpleVolume(GpuContext,SimpleStack3D) requested} since the
	 * last call to {@link #freeUnusedSimpleVolumes(GpuContext)}.
	 */
	void freeUnusedSimpleVolumes( GpuContext context );

	/**
	 * Free allocated resources associated to all stacks.
	 */
	void freeSimpleVolumes( GpuContext context );

	boolean upload( GpuContext context, SimpleStack3D stack );
}
