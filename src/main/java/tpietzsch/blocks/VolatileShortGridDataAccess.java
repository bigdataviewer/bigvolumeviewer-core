package tpietzsch.blocks;

import net.imglib2.RandomAccess;
import net.imglib2.img.basictypeaccess.volatiles.array.AbstractVolatileShortArray;
import net.imglib2.img.cell.Cell;

@Deprecated
public class VolatileShortGridDataAccess implements GridDataAccess< short[] >
{
	private final RandomAccess< ? extends Cell< ? extends AbstractVolatileShortArray< ? > > > cellAccess;

	public VolatileShortGridDataAccess( final RandomAccess< ? extends Cell< ? extends AbstractVolatileShortArray< ? > > > cellAccess )
	{
		this.cellAccess = cellAccess;
	}

	@Override
	public void fwd( final int d )
	{
		cellAccess.fwd( d );
	}

	@Override
	public void setPosition( final int position, final int d )
	{
		cellAccess.setPosition( position, d );
	}

	@Override
	public void setPosition( final int[] position )
	{
		cellAccess.setPosition( position );
	}

	@Override
	public int[] getPosition()
	{
		return new int[] { cellAccess.getIntPosition( 0 ), cellAccess.getIntPosition( 1 ), cellAccess.getIntPosition( 2 ) };
	}

	@Override
	public short[] get()
	{
		final AbstractVolatileShortArray< ? > data = cellAccess.get().getData();
		return data.isValid() ? data.getCurrentStorageArray() : null;
	}
}
