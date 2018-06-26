package tpietzsch.cache;

import java.util.Collection;
import java.util.List;
import tpietzsch.backend.GpuContext;
import tpietzsch.cache.PboChain.PboUploadBuffer;
import tpietzsch.cache.TextureCache.TileFillTask;

public class ProcessFillTasks
{
	public static void sequential(
			TextureCache textureCache,
			PboChain pboChain,
			GpuContext context,
			final Collection< ? extends FillTask > tasks ) throws InterruptedException
	{
		final List< TileFillTask > tileFillTasks = textureCache.stage( tasks );
		pboChain.init( tileFillTasks );
		final int numTasks = tileFillTasks.size();
		for ( int i = 0; i < numTasks; i++ )
		{
			pboChain.tryActivate( context );
			final PboUploadBuffer buf = pboChain.take();
			buf.task.fill( buf );
			pboChain.commit( buf );
			pboChain.tryUpload( context );
		}
		pboChain.flush();
		pboChain.tryUpload( context );
	}
}
