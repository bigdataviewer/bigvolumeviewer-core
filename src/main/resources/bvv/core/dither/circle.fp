out vec4 FragColor;

uniform vec2 viewportSize;
uniform vec2 dsp;

void main()
{
	// frag coord in NDC
	vec2 uv = 2 * ( gl_FragCoord.xy + dsp ) / viewportSize - 1;

	FragColor = ( length( uv ) < 1.0 )
		? ( ( uv.x > 0 || uv.y > 0 )
			? vec4( 1.0, 0.0, 1.0, 1 )
			: vec4( 0.0, 1.0, 0.0, 1 ) )
		: vec4( 0.2, 0.2, 0.2, 1 );
}
