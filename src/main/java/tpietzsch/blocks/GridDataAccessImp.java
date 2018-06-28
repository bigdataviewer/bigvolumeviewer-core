package tpietzsch.blocks;

import net.imglib2.RandomAccess;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileArrayDataAccess;
import net.imglib2.img.cell.Cell;

public class GridDataAccessImp
{
	abstract static class AbstractCells< T, A > implements GridDataAccess< T >
	{
		final RandomAccess< ? extends Cell< ? extends A > > cellAccess;

		AbstractCells( final RandomAccess< ? extends Cell< ? extends A > > cellAccess )
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
	}

	/**
	 * Access to primitive data of {@code Cell}s in a {@code CellImg}.
	 *
	 * @param <T>
	 *            primitive array type of cell data, e.g. {@code short[]}.
	 */
	public static class Cells< T > extends AbstractCells< T, ArrayDataAccess< ? > >
	{
		public Cells( final RandomAccess< ? extends Cell< ? extends ArrayDataAccess< ? > > > cellAccess )
		{
			super( cellAccess );
		}

		@SuppressWarnings( "unchecked" )
		@Override
		public T get()
		{
			return ( T ) cellAccess.get().getData().getCurrentStorageArray();
		}
	}

	/**
	 * Access to primitive data of volatile {@code Cell}s in a {@code CellImg}.
	 *
	 * @param <T>
	 *            primitive array type of cell data, e.g. {@code short[]}.
	 */
	public static class VolatileCells< T > extends AbstractCells< T, VolatileArrayDataAccess< ? >  >
	{
		public VolatileCells( final RandomAccess< ? extends Cell< ? extends VolatileArrayDataAccess< ? > > > cellAccess )
		{
			super( cellAccess );
		}

		@SuppressWarnings( "unchecked" )
		@Override
		public T get()
		{
			final VolatileArrayDataAccess< ? > data = cellAccess.get().getData();
			return data.isValid() ? ( T ) data.getCurrentStorageArray() : null;
		}
	}

	private GridDataAccessImp()
	{}
}
