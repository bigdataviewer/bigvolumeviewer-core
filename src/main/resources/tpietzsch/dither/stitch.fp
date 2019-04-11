out vec4 FragColor;

uniform sampler2D tex;
uniform vec2 viewportScale;
uniform vec2 spw;
uniform vec2 tls;

void main()
{
	vec2 xg = trunc( ( gl_FragCoord.xy - 0.5 ) * viewportScale );
	/*
	    Something is slightly off with floor() and mod() on AMD GPUs,
	    which causes floor(n*3/3) = n-1 and mod(n*3, 3) = 3 instead of
	    floor(n*3/3) = n and mod(n*3, 3) = 0 as expected on NVIDIA GPUs.
	    This leads to grid artifacts on the stitched image because tile
	    indices are off by one.
	    To fix this,
	        mod( xg, spw )
	    is replaced by
	        trunc( mod( xg + 0.1, spw ) )
	    below. (This works reliably because xg is always integral.)
	*/
	vec2 o = trunc( mod( xg + 0.1, spw ) );
	vec2 q = ( xg - o ) / spw + o * tls + 0.5;
	FragColor = texture( tex, q / textureSize( tex, 0 ) );
}
