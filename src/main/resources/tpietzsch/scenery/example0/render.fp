out vec4 FragColor;

in vec2 pos;

void main()
{
	FragColor = ( length( pos ) < 0.5 )
		? ( ( pos.x > 0 || pos.y > 0 )
			? vec4( 1.0, 0.0, 1.0, 1 )
			: vec4( 0.0, 1.0, 0.0, 1 ) )
		: vec4( 0.2, 0.2, 0.2, 1 );
}
