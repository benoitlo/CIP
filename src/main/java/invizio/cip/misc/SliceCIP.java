package invizio.cip.misc;


import java.io.IOException;

import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImagePlus;

import invizio.cip.CIP;
import invizio.cip.MetadataCIP2;
import invizio.cip.RaiCIP2;

import net.imagej.ImageJ;
import net.imagej.ops.AbstractOp;
import net.imagej.ops.OpService;
import net.imagej.ops.Op;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;


/**
 * 
 * @author Benoit Lombardot
 *
 */

	@Plugin(type = Op.class, name="SliceCIP", headless = true)
	public class SliceCIP  < T extends RealType<T> > extends AbstractOp 
	{
		
		
		
		@Parameter (type = ItemIO.INPUT)
		private RaiCIP2<T> inputImage;
		
		@Parameter( label="dimensions", persist=false ) 
		private Integer[] dimensions;

		@Parameter( label="position", persist=false ) 
		private Long[] position;

		@Parameter( label="method", persist=false, required=false ) // with persist and required set to false the parameter become optional
		private String method = "shallow";

		@Parameter (type = ItemIO.OUTPUT)
		private	RaiCIP2<T> outputImage;
		
		
		@Parameter
		OpService op;
		
		
		
		@Override
		public void run() {
			
			if( inputImage == null )
			{	//TODO: error message
				return;
			}
			
			int nDim = inputImage.numDimensions();
			long[] min = new long[nDim];
			long [] max = new long[nDim];
			inputImage.min(min);
			inputImage.max(max);
			
			if( dimensions != null && position != null  && dimensions.length == position.length )
			{	
				for( int i=0; i<dimensions.length; i++)
				{
					int d = dimensions[i];
					long x = position[i];
					min[d] = x;
					max[d] = x;
				}
			}
			else
			{
				// TODO: error message, dimensions and position should exist and have the same size 
				return;
			}
			
			
			RandomAccessibleInterval<T> temp = Views.offsetInterval( inputImage, new FinalInterval( min , max ) );
			temp = Views.dropSingletonDimensions( temp );
			
			RandomAccessibleInterval<T> raiTmp;
			if( method.toLowerCase().equals("deep") )
			{
				raiTmp= op.copy().rai( temp);
			}
			else {
				raiTmp = temp;
			}
			
			
			// adapt input metadata for the output
			MetadataCIP2 metadata = new MetadataCIP2( inputImage.metadata() );
			if ( dimensions != null && position != null )
			{
				metadata.dropDimensions( dimensions );
			}
			
			outputImage = new RaiCIP2<T>(raiTmp , metadata );
			
			
		}



		
		
		public static void main(final String... args) throws IOException
		{
			
			ImageJ ij = new ImageJ();
			ij.ui().showUI();
			
			ImagePlus imp = IJ.openImage(	CIP.class.getResource( "/mitosis_t1.tif" ).getFile()	);
			ij.ui().show(imp);
			
			
			//Img<UnsignedByteType> img = ImageJFunctions.wrap(imp);
			
			CIP cip = new CIP();
			cip.setContext( ij.getContext() );
			cip.setEnvironment( ij.op() );
			
			@SuppressWarnings("unchecked")
			RandomAccessibleInterval<?> output = (RandomAccessibleInterval<?>)
									cip.slice( imp , cip.list(2,3) , cip.list(1,2)  );
					
			//cip.create( cip.aslist(100, 50, 2) , 10, "double"  );
			
			String str = output==null ? "null" : output.toString();
			
			System.out.println("hello create image:" + str );
			ij.ui().show(output);
			
			System.out.println("done!");
		}
		
	
}
