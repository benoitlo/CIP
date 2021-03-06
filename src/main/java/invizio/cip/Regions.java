package invizio.cip;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.roi.labeling.LabelingType;
import net.imglib2.roi.util.IterableRandomAccessibleRegion;
import net.imglib2.type.BooleanType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;



/**
 * 
 * @author Benoit Lombardot
 *
 */


public class Regions {

	
	
	// TODO: create a function that receive rois and return list<RegionCIP>
	public static <B extends BooleanType<B>, T extends RealType<T>> Object ImagetoRegionCIP( RaiCIP2<T> raiCIP, String prefix )
	{
    	T valT = raiCIP.randomAccess().get();
    	if( valT instanceof BooleanType )
    	{
    		@SuppressWarnings("unchecked")
			RaiCIP2<B> mask = (RaiCIP2<B>)raiCIP;
    		return Regions.maskToRegionCIP( mask, prefix );
    	}
    	else {
    		return Regions.labelMapToRegionCIP( raiCIP, prefix );    		
    	}
	}
	
	
	
	private static <B extends BooleanType<B>, T extends RealType<T>> List<RegionCIP<B>> labelMapToRegionCIP( RaiCIP2<T> labelMap, String prefix )
	{
		if( prefix==null) {
			prefix="";
		}
		List<IterableRegion<B>> iterableRegions = labelMapToIterableRegions(labelMap);
		List<RegionCIP<B>> regions = new ArrayList<RegionCIP<B>>( iterableRegions.size() );
		int count=0;
		for( IterableRegion<B> iterableRegion : iterableRegions ) {
			final MetadataCIP2 metadata = new MetadataCIP2(labelMap.metadata() );
			final RegionCIP<B> region = new RegionCIP<B>( iterableRegion , metadata );
			region.name = prefix+count;
			regions.add( region );
			count++;
		}
		
		return regions;
	}
	
	
	private static <B extends BooleanType<B>> RegionCIP<B> maskToRegionCIP( RaiCIP2<B> mask , String name)
	{
		if( name==null) {
			name="region_"+mask.name;
		}
		final IterableRegion<B> iterableRegion = maskToIterableRegion( mask );
		final MetadataCIP2 metadata = new MetadataCIP2(mask.metadata() );
		final RegionCIP<B> region = new RegionCIP<B>( iterableRegion , metadata );
		region.name = name;
		return  region;
	}
	
	//////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////
	//
	// label map and masks to Iterable Regions
	//
	//////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////
	
	
//	public static <B extends BooleanType<B>, T extends RealType<T>> Object toIterableRegion( Object image, CIPService cipService )
//	{
//    	RaiCIP2<T> raiCIP = cipService.toRaiCIP( image );
//    	T valT = raiCIP.randomAccess().get();
//    	if( valT instanceof BooleanType )
//    	{
//    		@SuppressWarnings("unchecked")
//			RandomAccessibleInterval<B> mask = (RandomAccessibleInterval<B>)raiCIP;
//    		return Regions.maskToIterableRegion( mask );
//    	}
//    	else {
//    		return Regions.labelMapToIterableRegions( raiCIP );    		
//    	}
//	}
	
	
	// use realtype rather than integertype as input for flexibility reason 
	@SuppressWarnings("unchecked")
	private static <B extends BooleanType<B>, T extends RealType<T>> List<IterableRegion<B>> labelMapToIterableRegions( RandomAccessibleInterval<T> labelMap )
	{
		// create an imgLabeling
		
		
		Img<IntType> imgStorage = Util.getArrayOrCellImgFactory( labelMap, new IntType()).create(labelMap, new IntType() );
		ImgLabeling<Integer,IntType> imgLabeling = new ImgLabeling<Integer,IntType>( imgStorage );
		
		Cursor<T> cMap = Views.iterable( labelMap ).cursor();
		RandomAccess< LabelingType<Integer> > labelingRA = imgLabeling.randomAccess();
		while( cMap.hasNext() )
		{
			T valT = cMap.next();
			if( valT.getRealFloat() != 0 )
			{
				labelingRA.setPosition( cMap );
				LabelingType<Integer> labelingType = labelingRA.get();
				labelingType.add( (int) valT.getRealFloat() );
			}
		}
		
		// convert list of LabelRegions
		List< IterableRegion<B> > regions = new ArrayList< IterableRegion<B> >();
		LabelRegions<Integer> labelRegions = new LabelRegions<Integer>( imgLabeling );
		Set<Integer> labels = labelRegions.getExistingLabels();
		
		for( Integer label : labels )
			regions.add( (IterableRegion<B>) labelRegions.getLabelRegion(label) );
		
		return regions;
	}
	
	
	
	private static <B extends BooleanType<B>> IterableRegion<B> maskToIterableRegion( RandomAccessibleInterval<B> mask )
	{
		// getting min max position of the on pixel would allow to create a view 
		// on mask and reduce data to explore in further process
		int nDim = mask.numDimensions();
		long[] min = new long[nDim];
		long[] max = new long[nDim];
		for( int d=0; d<nDim; d++) {
			min[d] = mask.max(d);
			max[d] = mask.min(d);
		}
		
		Cursor<B> c = Views.iterable(mask).cursor();
		long[] pos = new long[nDim]; 
		while( c.hasNext() ) {
			B val = c.next();
			if( val.get() ) {
				c.localize(pos);
				for(int d=0; d<nDim ; d++) {
					if( pos[d]<min[d])
						min[d]=pos[d];
					if( pos[d]>max[d])
						max[d] = pos[d];
				}
			}
		}
		// create an IterableRandomAccessibleRegion
		return  IterableRandomAccessibleRegion.create(  Views.interval( mask , min , max )  );
	}


	
	
	
	
	
	
	
	
	
	//////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////
	//
	// IJ1 Rois to Iterable Regions
	//
	//////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////
	
	@SuppressWarnings("unchecked")
	public static <B extends BooleanType<B>> List<RegionCIP<B>> toRegionCIP( Object regions, String prefix )
	{
	
		List<RegionCIP<B>> regionCIPs = null;
		boolean prefixWasNull=false;
		if( prefix==null ){
			prefix="region";
			prefixWasNull = true;
		}
		// IterableRegion
    	if ( regions instanceof RegionCIP ) {
    		RegionCIP<B> region = (RegionCIP<B>) regions;
    		if( !prefixWasNull)
    			region.name = prefix;
    		regionCIPs = new ArrayList<RegionCIP<B>>();
    		regionCIPs.add( region );
    	}
    	
		// IterableRegion
    	else if ( regions instanceof IterableRegion ) {
    		RegionCIP<B> region = new RegionCIP<B>( (IterableRegion<B>) regions );
    		region.name = prefix;
    		regionCIPs = new ArrayList<RegionCIP<B>>();
    		regionCIPs.add( region );
    	}
    	
    	// Roi :we bet the roi is XY. if it as a position it could well be 3D, but let's not complicate things more ...
    	else if ( regions instanceof Roi) {
    		Roi roi = (Roi) regions;
    		regionCIPs = new ArrayList<RegionCIP<B>>();
    		final RegionCIP<B> region = new RegionCIP<B>( toIterableRegion2D(roi) );
    		if( !prefixWasNull)
    			region.name = roi.getName();
    		else
    			region.name = prefix;
    		regionCIPs.add( region );
    	}
    	
    	// List<?>
    	else if ( regions instanceof List) {
    		
    		if( ((List<?>)regions).size()>0 ) {
    			
    			Object item = ((List<Object>)regions).get(0);
    			
    			if( item instanceof RegionCIP ) {
    				regionCIPs = (List<RegionCIP<B>>) regions;
    				if( !prefixWasNull) {
    					int count=0;
        				for( RegionCIP<B> region : regionCIPs) {
        					region.name =  prefix + "_" + count;
        					count++;
        				}
    				}
    			}
    			
    			//List<IterableRegion>
    			else if( item instanceof IterableRegion ) {
    				List<IterableRegion<B>> iterRegions = (List<IterableRegion<B>>) regions;
    				int count=0;
    				for( IterableRegion<B> iterReg : iterRegions ) {
    					final RegionCIP<B> region = new RegionCIP<B>( iterReg );
    					region.name = prefix + "_" + count;
    					regionCIPs.add( region );
    					count++;
    				}
    			}
    			
    			// List<Roi>  // we assume the roi represent many region of dimensions xy
    			else if(item instanceof Roi) {
    				List<Roi> roiList = (List<Roi>) regions;
    				List<IterableRegion<B>> iterRegions = toIterableRegions2D( roiList );
    				int count=0;
    				for( IterableRegion<B> iterReg : iterRegions ) {
    					final RegionCIP<B> region = new RegionCIP<B>( iterReg );
    					if( !prefixWasNull)
    		    			region.name = roiList.get(count).getName();
    					else
    						region.name = prefix + "_" + count;
    					regionCIPs.add( region );
    					count++;
    				}
    			}
    			
    			// List<List<Roi>>  // looking at the Roi position one could guess the 3rd dimensions, for now Z by default
    			else if( item instanceof List ) {
    				if( ((List<?>)item).size()>0 && ((List<?>)item).get(0) instanceof Roi) {
    					List<List<Roi>> roisPerRegions = (List<List<Roi>>) regions;
    					List<IterableRegion<B>> iterRegions = toIterableRegions3D( roisPerRegions );
    					int count=0;
        				for( IterableRegion<B> iterReg : iterRegions ) {
        					final RegionCIP<B> region = new RegionCIP<B>( iterReg );
        					region.name = prefix + "_" + count;
        					regionCIPs.add( region );
        					count++;
        				}
    				}
    			}
    		}
    	}
		
		return regionCIPs;
	}

	
	
	
	@SuppressWarnings("unchecked")
	public static <B extends BooleanType<B>> List<IterableRegion<B>> toIterableRegion( Object regions )
	{
	
		List<IterableRegion<B>> iterRegions = null;
		// IterableRegion
    	if ( regions instanceof IterableRegion ) {
    		IterableRegion<B> region = (IterableRegion<B>) regions;
    		iterRegions = new ArrayList<IterableRegion<B>>();
    		iterRegions.add( region );
    	}
    	
    	// Roi
    	else if ( regions instanceof Roi) {
    		Roi roi = (Roi) regions;
    		iterRegions = new ArrayList<IterableRegion<B>>();
    		iterRegions.add( toIterableRegion2D(roi) );
    	}
    	
    	// List<?>
    	else if ( regions instanceof List) {
    		
    		if( ((List<?>)regions).size()>0 ) {
    			
    			Object item = ((List<Object>)regions).get(0);
    			
    			//List<IterableRegion>
    			if( item instanceof IterableRegion ) {
    				iterRegions = (List<IterableRegion<B>>) regions;
    			}
    			
    			// List<Roi>
    			else if(item instanceof Roi) {
    				List<Roi> roiList = (List<Roi>) regions;
    				iterRegions = toIterableRegions2D( roiList );
    			}
    			
    			// List<List<Roi>>
    			else if( item instanceof List ) {
    				if( ((List<?>)item).size()>0 && ((List<?>)item).get(0) instanceof Roi) {
    					List<List<Roi>> roisPerRegions = (List<List<Roi>>) regions;
    					iterRegions = toIterableRegions3D( roisPerRegions );
    				}
    			}
    			
    		}
    	}
		
		return iterRegions;
	}
	
	private static <B extends BooleanType<B>> List<IterableRegion<B>> toIterableRegions3D( List<List<Roi>> rois3D )
	{
		List<IterableRegion<B>> regions = new ArrayList<IterableRegion<B>>();
		for(List<Roi> roi3D : rois3D) {
			regions.add( toIterableRegion3D(roi3D) );
		}
		return regions;
	}
	
	private static <B extends BooleanType<B>> IterableRegion<B> toIterableRegion3D(List<Roi> rois)
	{
		int[] min = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		int[] max = new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for(Roi roi : rois) {
			Rectangle rect = roi.getBounds();
			int x = rect.x;
			int y = rect.y;
			int z = roi.getPosition()-1;
			int xM= x+rect.width;
			int yM= y+rect.height;
			
			if ( x < min[0])
				min[0] = x;
			if ( y < min[1])
				min[1] = y;
			if ( z < min[2])
				min[2] = z;
			if ( xM > max[0])
				max[0]= xM;
			if ( yM > max[1])
				max[1] = yM;
			if ( z > max[2])
				max[2] = z;
		}
		int width  = max[0] - min[0] + 1;
		int height = max[1] - min[1] + 1;
		int depth  = max[2] - min[2] + 1;
		
		ImageStack stack = new ImageStack(width, height, depth);
		for(Roi roi : rois ) {
			Rectangle rect = roi.getBounds();
			int z = roi.getPosition();
			
			ImageProcessor ip = new ByteProcessor( width, height);
			roi.setLocation(rect.x-min[0], rect.y-min[1]);
			roi.setPosition(0);
			ip.setValue(255);
			ip.fill(roi);
			roi.setLocation(rect.x, rect.y);
			roi.setPosition(z);
			
			stack.setProcessor(ip, z-min[2]);
		}
		ImagePlus imp = new ImagePlus("3D Mask", stack );
		
		long[] min2 = new long[3];
		for (int d=0 ; d<3; d++)
			min2[d] = - min[d]; 
		
		return imagePlusToIterableRegion( imp, min2);
	}
	
	
	
	private static <B extends BooleanType<B>> List<IterableRegion<B>> toIterableRegions2D(List<Roi> rois)
	{
		List<IterableRegion<B>> regions = new ArrayList<IterableRegion<B>>();
		for(Roi roi : rois) {
			regions.add( toIterableRegion2D(roi) );
		}
		return regions;
	}
	
	
	
	private static <B extends BooleanType<B>> IterableRegion<B> toIterableRegion2D(Roi roi)
	{
		Rectangle rect = roi.getBounds();
		long[] min = new long[] { -rect.x, -rect.y};
		ImageProcessor ip = new ByteProcessor( rect.width, rect.height);
		roi.setLocation(0, 0);
		ip.setValue(255);
		ip.fill(roi);
		roi.setLocation(rect.x, rect.y);
		//ip.setBinaryThreshold();
		ImagePlus imp = new ImagePlus("mask_"+roi.getName() , ip );
		
		return imagePlusToIterableRegion( imp, min);
	}
	
	private static <B extends BooleanType<B>> IterableRegion<B> imagePlusToIterableRegion( ImagePlus imp, long[] min)
	{
		RandomAccessibleInterval<UnsignedByteType> rai = ImageJFunctions.wrap(imp);
		rai = Views.offset(rai , min );
		
		@SuppressWarnings("unchecked")
		RandomAccessibleInterval<B> mask = (RandomAccessibleInterval<B>) Converters.convert(rai, ( i, o ) -> o.set( i.get()>0 ),  new BitType() );
		
		return IterableRandomAccessibleRegion.create( mask );
	}
	
	
	
	
	
	
	//////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////
	//
	// Iterable regions to IJ1 Rois
	//
	//////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////

		
	@SuppressWarnings("unchecked")
	public static <B extends BooleanType<B>> List<List<Roi>> toIJ1ROI( Object regions )
	{
		List<IterableRegion<B>> regions2 = null;
		List<List<Roi>> roisPerRegions = null;
		
		// IterableRegion
    	if ( regions instanceof IterableRegion ) {
    		IterableRegion<B> region = (IterableRegion<B>) regions;
    		regions2 = new ArrayList<IterableRegion<B>>();
    		regions2.add( region );
    		roisPerRegions = toIJ1ROI( regions2 );
    	}
    	
    	// Roi
    	else if ( regions instanceof Roi) {
    		Roi roi = (Roi) regions;
    		List<Roi> roiList = new ArrayList<Roi>();
    		roiList.add(roi);
    		roisPerRegions = new ArrayList<List<Roi>>();
			roisPerRegions.add( roiList );
    	}
    	
    	// List<?>
    	else if ( regions instanceof List) {
    		
    		if( ((List<?>)regions).size()>0 ) {
    			
    			Object item = ((List<Object>)regions).get(0);
    			
    			//List<IterableRegion>
    			if( item instanceof IterableRegion ) {
    				regions2 = (List<IterableRegion<B>>) regions;
    				roisPerRegions = toIJ1ROI( regions2 );
    			}
    			
    			// List<Roi>
    			else if(item instanceof Roi) {
    				List<Roi> roiList = (List<Roi>) regions;
    				roisPerRegions = new ArrayList<List<Roi>>();
    				for( Roi roi : roiList) {
    					List<Roi> roiList2 = new ArrayList<Roi>();
    					roiList2.add(roi);
    					roisPerRegions.add( roiList2  );
    				}
    			}
    			
    			// List<List<Roi>>
    			else if( item instanceof List ) {
    				if( ((List<?>)item).size()>0 && ((List<Object>)item).get(0) instanceof Roi) {
    					roisPerRegions = (List<List<Roi>>) regions;
    				}
    			}
    			
    		}
    	}
    	
    	return roisPerRegions; 
	}
	
	
	
	
	public static <B extends BooleanType<B>> List<List<Roi>> toIJ1ROI( List<IterableRegion<B>> regions )
	{
		List<List<Roi>> roiListPerRegion = new ArrayList<List<Roi>>();
		for(IterableRegion<B> region : regions)
		{
			roiListPerRegion.add( toIJ1ROI( region ) );
		}
		return roiListPerRegion;
	}
	
	
	
	
	
	public static <B extends BooleanType<B>> List<Roi> toIJ1ROI( IterableRegion<B> region)// , CIPService cipService )
	{
		// convert the region to an image plus mask
		int nDim = region.numDimensions();
		
		//long[] min = new long[nDim];
		//region.min(min);
		//RandomAccessibleInterval<B> rai = Views.offset( region , min );  // necessary when converting via IJ2 but not with
		
		//ImagePlus impReg = cipService.toImagegPlus( rai ); // very slow as compared to ImageJFunction even on small images
		
		//impReg.setTitle("test impReg");
		//impReg.show();
		
		ImagePlus impReg = ImageJFunctions.wrap( region , "test"); // gives a virtual stack
		
		IJ.setRawThreshold(impReg, 1, 255, null);
		ThresholdToSelection roiMaker = new ThresholdToSelection();
		
		int x0 = (int)region.min(0); // region have no information on axes type so we default x,y to the 2 first axis
		int y0 = (int)region.min(1); // have region metadata would allow to keep track of that
		int z0 = 0;
		if( nDim>2 )
		{
			z0 = (int)region.min(2);
		}
		
		//System.out.println("x0 "+x0+" ; y0 "+y0+" ; z0 "+z0);
		
		// Iterate on the plane of the imageplus and create a roi on each plane
		List<Roi> roiList = new ArrayList<Roi>();
		
		int nSlice = impReg.getStackSize();
		
		//impReg.show();
		//System.out.println( "min thresh : " + impReg.getProcessor().getMinThreshold() );
		//System.out.println( "max thresh : " + impReg.getProcessor().getMaxThreshold() );
		
		if( nSlice==1 )
		{
			Roi roi = roiMaker.convert( impReg.getProcessor() );
			if( roi !=null ) {
				Rectangle rect  = roi.getBounds();
				roi.setLocation( x0+rect.x , y0 + rect.y );
				roi.setPosition( z0 + 1 );
			}
			roiList.add(roi);
		}
		else
		{
			ImageStack stack = impReg.getStack();
			for(int i=1; i<=nSlice; i++)
			{
				ImageProcessor ip  = stack.getProcessor(i);
				ip.setThreshold(1.0, 255.0, 0);				// added on 2018-06-11 to correct a regression
				Roi roi = roiMaker.convert( ip );   // this could be slow, lets see
				if( roi!=null ) {
					Rectangle rect  = roi.getBounds();
					roi.setLocation( x0+rect.x , y0 + rect.y );
					roi.setPosition( z0 + i );
					roiList.add(roi);
				}
			}
		}
		
		return roiList;
	}
	
	
	public static void main(final String... args)
	{
		
		Roi roi = new Roi(50,50, 100, 50);
		
		IterableRegion<?> region = toIterableRegion2D(roi);
		System.out.println("region:" + region.toString() );
		
	}
	
	
}
