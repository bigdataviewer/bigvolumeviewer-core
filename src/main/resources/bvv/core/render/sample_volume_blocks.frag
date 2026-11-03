uniform mat4 im;
uniform vec3 sourcemin;
uniform vec3 sourcemax;

void intersectBoundingBox( vec4 wfront, vec4 wback, out float tnear, out float tfar )
{
	vec4 mfront = im * wfront;
	vec4 mback = im * wback;
	intersectBox( mfront.xyz, (mback - mfront).xyz, sourcemin, sourcemax, tnear, tfar );
}

uniform vec3 blockScales[ NUM_BLOCK_SCALES ];
uniform vec3 lutOffset;
uniform vec3 lutSize; // Local volume LUT size (in tiles)
uniform int cacheType;
uniform int cacheLutZOffset;

float sampleVolume( vec4 wpos )
{
	vec3 pos = (im * wpos).xyz + 0.5;
	
	vec3 q = floor( pos / cacheBlockSize ) - lutOffset;
	
	// Prevent spillage into other volumes' LUT slices
    if ( any( lessThan( q, vec3( 0.0 ) ) ) || any( greaterThanEqual( q, lutSize ) ) )
    {
        return 0.0;
    }
    q += 0.5;
	q.z += cacheLutZOffset; 

	uvec4 lutv = texture( globalCacheLut, q / globalCacheLutSize );
	vec3 B0 = lutv.xyz * paddedBlockSize + cachePadOffset;
	vec3 sj = blockScales[ lutv.w ];
	
	vec3 c0 = B0 + mod(pos * sj, cacheBlockSize);
	
	return texture( u_Caches[cacheType], c0/ cacheSize[cacheType]  ).r;	
}