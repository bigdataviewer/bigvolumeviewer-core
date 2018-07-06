#define NUM_BLOCK_SCALES 10

out vec4 FragColor;

uniform vec2 viewportSize;

uniform mat4 ipv;

// -- source 1 -----------------
uniform mat4 im;
uniform vec3 sourcemin;
uniform vec3 sourcemax;
uniform usampler3D lut;
// from LUT
uniform vec3 blockScales[ NUM_BLOCK_SCALES ];
uniform vec3 lutScale;
uniform vec3 lutOffset;

void intersectBox( vec3 r_o, vec3 r_d, vec3 boxmin, vec3 boxmax, out float tnear, out float tfar );

float blockTexture( vec4 wpos, sampler3D volumeCache, vec3 cacheSize, vec3 blockSize, vec3 paddedBlockSize, vec3 padOffset )
{
	vec3 pos = (im * wpos).xyz + 0.5;
	vec3 q = pos * lutScale - lutOffset;

	uvec4 lutv = texture( lut, q );
	vec3 B0 = lutv.xyz * paddedBlockSize + padOffset;
	vec3 sj = blockScales[ lutv.w ];

	vec3 c0 = B0 + mod( pos * sj, blockSize ) + 0.5 * sj;
	                                       // + 0.5 ( sj - 1 )   + 0.5 for tex coord offset

	return texture( volumeCache, c0 / cacheSize ).r;
}

void intersectBoundingBox( vec4 wfront, vec4 wback, out float tnear, out float tfar )
{
	vec4 mfront = im * wfront;
	vec4 mback = im * wback;
	intersectBox( mfront.xyz, (mback - mfront).xyz, sourcemin, sourcemax, tnear, tfar );
}

// -----------------------------


uniform sampler3D volumeCache;


// -- comes from CacheSpec -----
uniform vec3 blockSize;
uniform vec3 paddedBlockSize;
uniform vec3 cachePadOffset;
// -----------------------------



// -- comes from TextureCache --
uniform vec3 cacheSize; // TODO: get from texture!?
// -----------------------------








uniform float intensity_offset;
uniform float intensity_scale;
vec4 convert( float v )
{
	return vec4( vec3( intensity_offset + intensity_scale * v ), 1 );
}

// intersect ray with a box
// http://www.siggraph.org/education/materials/HyperGraph/raytrace/rtinter3.htm
void intersectBox( vec3 r_o, vec3 r_d, vec3 boxmin, vec3 boxmax, out float tnear, out float tfar )
{
	// compute intersection of ray with all six bbox planes
	vec3 invR = 1 / r_d;
	vec3 tbot = invR * ( boxmin - r_o );
	vec3 ttop = invR * ( boxmax - r_o );

	// re-order intersections to find smallest and largest on each axis
	vec3 tmin = min(ttop, tbot);
	vec3 tmax = max(ttop, tbot);

	// find the largest tmin and the smallest tmax
	tnear = max( max( tmin.x, tmin.y ), max( tmin.x, tmin.z ) );
	tfar = min( min( tmax.x, tmax.y ), min( tmax.x, tmax.z ) );
}

void main()
{
	// frag coord in NDC
	vec2 uv = 2 * gl_FragCoord.xy / viewportSize - 1;

	// NDC of frag on near and far plane
	vec4 front = vec4( uv, -1, 1 );
	vec4 back = vec4( uv, 1, 1 );

	// calculate eye ray in world space
	vec4 wfront = ipv * front;
	wfront *= 1 / wfront.w;
	vec4 wback = ipv * back;
	wback *= 1 / wback.w;


	// -- bounding box intersection for volume X ---------------------------------------
	float tnear, tfar;
	intersectBoundingBox( wfront, wback, tnear, tfar );
	tnear = max( 0, tnear );
	tfar = min( 1, tfar );
	// -------------------------------------------------------



	if ( tnear < tfar )
	{
		vec4 fb = wback - wfront;
		vec4 wpos = mix( wfront, wback, tnear );
		vec4 wstep = normalize( fb );
		int numSteps = int( trunc( ( tfar - tnear ) * length( fb ) ) + 1 );

		float v = 0;
		for ( int i = 0; i < numSteps; ++i, wpos += wstep )
		{
			float x = blockTexture( wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset );
			v = max( v, x );
		}
		FragColor = convert( v );
	}
	else
		discard;
}
